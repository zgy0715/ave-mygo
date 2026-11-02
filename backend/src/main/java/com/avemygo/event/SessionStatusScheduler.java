package com.avemygo.event;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场次/演出状态机定时任务（docs/05 §状态机：实体注释承诺的行为在这里兑现）。
 *
 * 规则（每 60 秒一轮，全部幂等）：
 *  ① NOT_OPEN 且已到 sale_start_time 且未散场 → ONSALE
 *  ② NOT_OPEN / ONSALE 且已过 end_time → ENDED
 *  ③ PUBLISHED 且所有场次都已散场 → ENDED
 *
 * 与后台手动改状态不冲突：手动把场次设为 ONSALE 后会跳过 ①；手动设为 ENDED 不会被回滚。
 * 例外：手动设 NOT_OPEN 但开售时间已过，下一轮会被自动开售（符合"到点即售"的语义）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStatusScheduler {

    private final LiveSessionRepository sessionRepository;
    private final EventRepository eventRepository;

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    @Transactional
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int activated = sessionRepository.activateDueSessions(now);
        int finishedSessions = sessionRepository.finishEndedSessions(now);
        int finishedEvents = eventRepository.finishEndedEvents(now);
        if (activated + finishedSessions + finishedEvents > 0) {
            log.info("场次状态机：开售 {} 场，结束 {} 场，演出结束 {} 场", activated, finishedSessions, finishedEvents);
        }
    }
}
