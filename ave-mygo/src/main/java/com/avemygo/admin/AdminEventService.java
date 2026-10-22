package com.avemygo.admin;

import com.avemygo.admin.dto.AdminEventDtos.EventPatch;
import com.avemygo.admin.dto.AdminEventDtos.EventUpsert;
import com.avemygo.admin.dto.AdminEventDtos.EventView;
import com.avemygo.admin.dto.AdminEventDtos.SessionPatch;
import com.avemygo.admin.dto.AdminEventDtos.SessionUpsert;
import com.avemygo.admin.dto.AdminEventDtos.SessionView;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypePatch;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypeUpsert;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypeView;
import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSession;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.SetlistItemRepository;
import com.avemygo.event.TicketType;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.order.TicketOrderRepository;
import com.avemygo.song.Band;
import com.avemygo.song.BandRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端演出域业务：演出 / 场次 / 票档的查询与写入（docs/04 管理端 P0）。
 * 规则集中在本层：
 *  - 演出状态只允许 DRAFT/PUBLISHED/ENDED；DRAFT 前台不可见（前台查询已过滤）
 *  - 场次开演 < 结束；场次必须挂在存在的演出下
 *  - 票档库存不得小于已售数量（改小会破 chk_stock 或造成数据失真）
 *  - 价格保留两位小数、限购 1-10
 */
@Service
@RequiredArgsConstructor
public class AdminEventService {

    private static final Set<String> EVENT_STATUS = Set.of("DRAFT", "PUBLISHED", "ENDED");
    private static final Set<String> SESSION_STATUS = Set.of("NOT_OPEN", "ONSALE", "ENDED");

    private final EventRepository eventRepository;
    private final LiveSessionRepository sessionRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final BandRepository bandRepository;
    private final TicketOrderRepository orderRepository;
    private final SetlistItemRepository setlistItemRepository;

    /** 未完成订单状态：存在任一即禁止删除演出/场次（已退款/已取消/已过期不限） */
    private static final Set<String> UNFINISHED_ORDER_STATUS = Set.of("PENDING_PAYMENT", "PAID");

    // ---------------- 演出 ----------------

    @Transactional(readOnly = true)
    public Page<EventView> events(String status, Short bandId, int page, int pageSize) {
        Page<LiveEvent> events = eventRepository.searchAdmin(
                status == null || status.isBlank() ? null : status.trim().toUpperCase(), bandId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), 50),
                        Sort.by(Sort.Direction.DESC, "id")));
        // 乐队名批量取，避免 N+1
        Map<Short, String> bandNames = bandRepository.findAllById(
                        events.map(LiveEvent::getBandId).stream().distinct().toList())
                .stream().collect(Collectors.toMap(Band::getId, Band::getName));
        // 场次数批量取
        List<Long> ids = events.map(LiveEvent::getId).toList();
        Map<Long, Long> counts = ids.isEmpty() ? Map.of()
                : sessionRepository.findByEventIdIn(ids).stream()
                        .collect(Collectors.groupingBy(LiveSession::getEventId, Collectors.counting()));
        return events
                .map(e -> toView(e, bandNames.get(e.getBandId()), counts.getOrDefault(e.getId(), 0L).intValue()));
    }

    @Transactional
    public EventView create(EventUpsert req) {
        requireBand(req.bandId());
        LiveEvent e = new LiveEvent();
        e.setTitle(req.title().trim());
        e.setBandId(req.bandId());
        e.setDescription(req.description());
        e.setPosterUrl(req.posterUrl());
        e.setVenue(req.venue().trim());
        e.setStatus(validEventStatus(req.status(), "DRAFT"));
        return toView(eventRepository.save(e), bandName(req.bandId()), 0);
    }

    @Transactional
    public EventView update(Long id, EventPatch req) {
        LiveEvent e = eventRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "演出不存在"));
        if (req.title() != null && !req.title().isBlank()) e.setTitle(req.title().trim());
        if (req.bandId() != null) {
            requireBand(req.bandId());
            e.setBandId(req.bandId());
        }
        if (req.description() != null) e.setDescription(req.description());
        if (req.posterUrl() != null) e.setPosterUrl(req.posterUrl().isBlank() ? null : req.posterUrl());
        if (req.venue() != null && !req.venue().isBlank()) e.setVenue(req.venue().trim());
        if (req.status() != null) e.setStatus(validEventStatus(req.status(), e.getStatus()));
        return toView(eventRepository.save(e), bandName(e.getBandId()),
                (int) sessionRepository.countByEventId(e.getId()));
    }

    /**
     * 删除演出（软删）。存在待支付/已支付订单时拒绝 —— 钱和票还没两清，
     * 删了演出订单就成孤儿；已退款/已取消/已过期不拦截。
     */
    @Transactional
    public void deleteEvent(Long id) {
        LiveEvent e = eventRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "演出不存在"));
        if (orderRepository.existsByEventIdAndStatusIn(id, UNFINISHED_ORDER_STATUS)) {
            throw new BizException(40903, "该演出存在待支付或已支付订单，请先处理订单后再删除");
        }
        e.setIsDeleted((byte) 1);
        eventRepository.save(e);
    }

    /**
     * 删除场次（软删），级联软删其票档与曲目单。
     * 该场次存在未完成订单时拒绝；已退款/已取消/已过期不拦截。
     */
    @Transactional
    public void deleteSession(Long id) {
        LiveSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "场次不存在"));
        if (orderRepository.existsBySessionIdAndStatusIn(id, UNFINISHED_ORDER_STATUS)) {
            throw new BizException(40903, "该场次存在待支付或已支付订单，请先处理订单后再删除");
        }
        ticketTypeRepository.softDeleteBySessionId(id);
        setlistItemRepository.softDeleteBySessionId(id);
        s.setIsDeleted((byte) 1);
        sessionRepository.save(s);
    }

    // ---------------- 场次 ----------------

    @Transactional(readOnly = true)
    public List<SessionView> sessions(Long eventId) {
        eventRepository.findById(eventId).orElseThrow(() -> new BizException(40004, "演出不存在"));
        List<LiveSession> sessions = sessionRepository.findByEventIdOrderByStartTimeAsc(eventId);
        Map<Long, List<TicketType>> types = sessions.isEmpty() ? Map.of()
                : ticketTypeRepository.findBySessionIdIn(sessions.stream().map(LiveSession::getId).toList())
                        .stream().collect(Collectors.groupingBy(TicketType::getSessionId));
        return sessions.stream()
                .map(s -> toView(s, types.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional
    public SessionView createSession(Long eventId, SessionUpsert req) {
        eventRepository.findById(eventId).orElseThrow(() -> new BizException(40004, "演出不存在"));
        validatePeriod(req.startTime(), req.endTime());
        LiveSession s = new LiveSession();
        s.setEventId(eventId);
        s.setName(req.name().trim());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setSaleStartTime(req.saleStartTime() == null ? LocalDateTime.now() : req.saleStartTime());
        s.setStatus(validSessionStatus(req.status(), "NOT_OPEN"));
        return toView(sessionRepository.save(s), List.of());
    }

    @Transactional
    public SessionView updateSession(Long id, SessionPatch req) {
        LiveSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "场次不存在"));
        if (req.name() != null && !req.name().isBlank()) s.setName(req.name().trim());
        LocalDateTime start = req.startTime() == null ? s.getStartTime() : req.startTime();
        LocalDateTime end = req.endTime() == null ? s.getEndTime() : req.endTime();
        validatePeriod(start, end);
        s.setStartTime(start);
        s.setEndTime(end);
        if (req.saleStartTime() != null) s.setSaleStartTime(req.saleStartTime());
        if (req.status() != null) s.setStatus(validSessionStatus(req.status(), s.getStatus()));
        List<TicketType> types = ticketTypeRepository.findBySessionIdOrderByIdAsc(id);
        return toView(sessionRepository.save(s), types);
    }

    // ---------------- 票档 ----------------

    @Transactional
    public TicketTypeView createTicketType(Long sessionId, TicketTypeUpsert req) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(40004, "场次不存在"));
        TicketType t = new TicketType();
        t.setSessionId(sessionId);
        t.setName(req.name().trim());
        t.setPrice(req.price());
        t.setTotalStock(req.totalStock());
        t.setMaxPerUser((byte) (req.maxPerUser() == null ? 4 : req.maxPerUser()));
        t.setStatus(req.status() == null ? (byte) 1 : req.status());
        return toView(ticketTypeRepository.save(t));
    }

    @Transactional
    public TicketTypeView updateTicketType(Long id, TicketTypePatch req) {
        TicketType t = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new BizException(40004, "票档不存在"));
        if (req.name() != null && !req.name().isBlank()) t.setName(req.name().trim());
        if (req.price() != null) t.setPrice(req.price());
        if (req.totalStock() != null) {
            if (req.totalStock() < t.getSoldCount()) {
                throw new BizException(40001,
                        "库存不能小于已售数量（已售 " + t.getSoldCount() + " 张）");
            }
            t.setTotalStock(req.totalStock());
        }
        if (req.maxPerUser() != null) t.setMaxPerUser((byte) req.maxPerUser().intValue());
        if (req.status() != null) t.setStatus(req.status());
        return toView(ticketTypeRepository.save(t));
    }

    // ---------------- 私有工具 ----------------

    private void validatePeriod(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BizException(40001, "结束时间必须晚于开演时间");
        }
    }

    private String validEventStatus(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim().toUpperCase();
        if (!EVENT_STATUS.contains(s)) throw new BizException(40001, "演出状态不合法：" + raw);
        return s;
    }

    private String validSessionStatus(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim().toUpperCase();
        if (!SESSION_STATUS.contains(s)) throw new BizException(40001, "场次状态不合法：" + raw);
        return s;
    }

    private void requireBand(Short bandId) {
        if (bandId == null || !bandRepository.existsById(bandId)) {
            throw new BizException(40001, "乐队不存在");
        }
    }

    private String bandName(Short bandId) {
        return bandRepository.findById(bandId).map(Band::getName).orElse(null);
    }

    private EventView toView(LiveEvent e, String bandName, int sessionCount) {
        return new EventView(e.getId(), e.getTitle(), e.getBandId(), bandName,
                e.getDescription(), e.getPosterUrl(), e.getVenue(), e.getStatus(), sessionCount);
    }

    private SessionView toView(LiveSession s, List<TicketType> types) {
        return new SessionView(s.getId(), s.getEventId(), s.getName(),
                s.getStartTime(), s.getEndTime(), s.getSaleStartTime(), s.getStatus(),
                types.stream().map(this::toView).toList());
    }

    private TicketTypeView toView(TicketType t) {
        return new TicketTypeView(t.getId(), t.getSessionId(), t.getName(), t.getPrice(),
                t.getTotalStock(), t.getSoldCount(),
                t.getTotalStock() - t.getSoldCount(), t.getMaxPerUser(), t.getStatus());
    }
}
