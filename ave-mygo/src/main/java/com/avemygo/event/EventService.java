package com.avemygo.event;

import com.avemygo.common.BizException;
import com.avemygo.event.dto.EventDtos.EventDetail;
import com.avemygo.event.dto.EventDtos.EventItem;
import com.avemygo.event.dto.EventDtos.SessionView;
import com.avemygo.event.dto.EventDtos.TicketTypeView;
import com.avemygo.song.Band;
import com.avemygo.song.BandRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演出业务层。
 * 列表页用两次 IN 批量查询取场次和票档，防 N+1；
 * minPrice / availableStock / 售罄 全部实时派生，绝不落库。
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final LiveSessionRepository sessionRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final BandRepository bandRepository;

    /** 前台演出列表（DRAFT 永远不可见） */
    public Page<EventItem> list(String status, Short bandId, int page, int pageSize) {
        Page<LiveEvent> events = eventRepository.search(status, bandId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(pageSize, 1), 50),
                        Sort.by(Sort.Direction.DESC, "id")));

        // 两次 IN 批量查询：本页所有场次 + 所有票档
        List<Long> eventIds = events.map(LiveEvent::getId).getContent();
        Map<Long, List<LiveSession>> sessionsByEvent = group(
                sessionRepository.findByEventIdIn(eventIds), LiveSession::getEventId);
        Map<Long, List<TicketType>> typesBySession = group(
                ticketTypeRepository.findBySessionIdIn(
                        sessionsByEvent.values().stream().flatMap(List::stream).map(LiveSession::getId).toList()),
                TicketType::getSessionId);

        Map<Short, String> bandNames = bandRepository.findAll().stream()
                .collect(Collectors.toMap(Band::getId, Band::getName));

        return events.map(e -> {
            List<TicketType> types = sessionsByEvent.getOrDefault(e.getId(), List.of()).stream()
                    .flatMap(s -> typesBySession.getOrDefault(s.getId(), List.of()).stream())
                    .filter(t -> t.getStatus() == 1)
                    .toList();
            BigDecimal minPrice = types.stream().map(TicketType::getPrice)
                    .min(BigDecimal::compareTo).orElse(null);
            LocalDateTime nextTime = sessionsByEvent.getOrDefault(e.getId(), List.of()).stream()
                    .map(LiveSession::getStartTime)
                    .filter(t -> t.isAfter(LocalDateTime.now()))
                    .min(LocalDateTime::compareTo).orElse(null);
            return new EventItem(e.getId(), e.getTitle(), bandNames.get(e.getBandId()),
                    e.getPosterUrl(), e.getVenue(), e.getStatus(), minPrice, nextTime);
        });
    }

    /** 演出详情：场次 + 票档 + 实时余票 */
    @Transactional(readOnly = true)
    public EventDetail detail(Long id) {
        LiveEvent e = eventRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "演出不存在"));
        if ("DRAFT".equals(e.getStatus())) {
            throw new BizException(40401, "演出不存在");   // 草稿对前台不可见
        }

        String bandName = bandRepository.findById(e.getBandId()).map(Band::getName).orElse(null);
        List<SessionView> sessions = sessionRepository.findByEventIdOrderByStartTimeAsc(id).stream()
                .map(s -> new SessionView(
                        s.getId(), s.getName(), s.getStartTime(), s.getEndTime(),
                        s.getStatus(), s.getSaleStartTime(),
                        ticketTypeRepository.findBySessionIdAndStatusOrderByIdAsc(s.getId(), (byte) 1)
                                .stream().map(this::toView).toList()))
                .toList();

        return new EventDetail(e.getId(), e.getTitle(), bandName, e.getDescription(),
                e.getPosterUrl(), e.getVenue(), e.getStatus(), sessions);
    }

    private TicketTypeView toView(TicketType t) {
        return new TicketTypeView(t.getId(), t.getName(), t.getPrice(),
                t.availableStock(), t.getTotalStock(), t.getMaxPerUser(), t.getStatus());
    }

    private static <T, K> Map<K, List<T>> group(List<T> list, Function<T, K> key) {
        return list.stream().collect(Collectors.groupingBy(key));
    }
}
