package com.avemygo.reminder;

import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSessionRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 开售提醒业务。
 *
 * 订阅/取消都是幂等（前端可放心双击）。"已提醒"由定时任务判定：
 * 只要该演出存在任一"已到开售时间"的场次，就把 notified 置 1。
 * 站内没有推送通道，这里是"状态位 + 前端查询"的实现；
 * 将来接邮件/微信推送只需在 markDueNotified 之后加发送动作（表结构不用改）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final EventReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final LiveSessionRepository sessionRepository;

    public record ReminderView(
            Long eventId, String eventTitle, String venue, String eventStatus,
            String posterUrl, LocalDateTime saleStartTime, boolean notified
    ) {
    }

    @Transactional(readOnly = true)
    public List<ReminderView> myReminders(Long userId) {
        List<EventReminder> list = reminderRepository.findByUserIdOrderByIdDesc(userId);
        if (list.isEmpty()) return List.of();

        Map<Long, LiveEvent> events = new HashMap<>();
        eventRepository.findAllById(list.stream().map(EventReminder::getEventId).toList())
                .forEach(e -> events.put(e.getId(), e));

        Map<Long, LocalDateTime> saleStarts = new HashMap<>();
        for (Object[] row : sessionRepository.nextSaleStartByEvent(
                list.stream().map(EventReminder::getEventId).toList(), LocalDateTime.now())) {
            saleStarts.put(((Number) row[0]).longValue(), (LocalDateTime) row[1]);
        }

        return list.stream()
                .map(r -> {
                    LiveEvent e = events.get(r.getEventId());
                    if (e == null) return null;                       // 演出已删
                    return new ReminderView(e.getId(), e.getTitle(), e.getVenue(), e.getStatus(),
                            e.getPosterUrl(), saleStarts.get(e.getId()), r.getNotified() == 1);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public ReminderView subscribe(Long userId, Long eventId) {
        LiveEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BizException(40401, "演出不存在"));
        if ("DRAFT".equals(event.getStatus())) {
            throw new BizException(40401, "演出不存在");
        }
        if ("ENDED".equals(event.getStatus())) {
            throw new BizException(40902, "演出已结束，无需提醒");
        }
        Optional<EventReminder> existing = reminderRepository.findByUserIdAndEventId(userId, eventId);
        EventReminder r = existing.orElseGet(() -> {
            EventReminder created = new EventReminder();
            created.setUserId(userId);
            created.setEventId(eventId);
            return reminderRepository.save(created);
        });
        LocalDateTime saleStart = nextSaleStart(eventId);
        return new ReminderView(event.getId(), event.getTitle(), event.getVenue(), event.getStatus(),
                event.getPosterUrl(), saleStart, r.getNotified() == 1);
    }

    @Transactional
    public void unsubscribe(Long userId, Long eventId) {
        reminderRepository.findByUserIdAndEventId(userId, eventId)
                .ifPresent(reminderRepository::delete);
    }

    /** 定时任务：每分钟把到点的订阅标记为已提醒（幂等） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void tick() {
        int changed = reminderRepository.markDueNotified(LocalDateTime.now());
        if (changed > 0) {
            log.info("开售提醒：{} 条订阅已到点", changed);
        }
    }

    private LocalDateTime nextSaleStart(Long eventId) {
        List<Object[]> rows = sessionRepository.nextSaleStartByEvent(List.of(eventId), LocalDateTime.now());
        return rows.isEmpty() ? null : (LocalDateTime) rows.get(0)[1];
    }
}
