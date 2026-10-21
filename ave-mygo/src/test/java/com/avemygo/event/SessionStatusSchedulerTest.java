package com.avemygo.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 状态机定时任务（回归：此前实体注释承诺自动开售/结束，但没有实现）。
 * 直接调用 tick()，不依赖真实等待。
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionStatusSchedulerTest {

    @Autowired private SessionStatusScheduler scheduler;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM ticket_type");
        jdbc.update("DELETE FROM live_session");
        jdbc.update("DELETE FROM live_event");
    }

    private long event(String status) {
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[TEST] 状态机',1,'场馆',?,0,NOW(),NOW())", status);
        return jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
    }

    private long session(long eventId, String status, LocalDateTime start, LocalDateTime end, LocalDateTime saleStart) {
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,0,NOW(),NOW())",
                eventId, "场次", start, end, saleStart, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
    }

    private String sessionStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM live_session WHERE id = ?", String.class, id);
    }

    private String eventStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM live_event WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("到开售时间 → NOT_OPEN 自动变 ONSALE")
    void activatesDueSession() {
        long e = event("PUBLISHED");
        LocalDateTime now = LocalDateTime.now();
        long s = session(e, "NOT_OPEN", now.plusDays(1), now.plusDays(1).plusHours(2), now.minusMinutes(5));

        scheduler.tick();

        assertThat(sessionStatus(s)).isEqualTo("ONSALE");
    }

    @Test
    @DisplayName("未到开售时间 → 保持 NOT_OPEN")
    void keepsFutureSession() {
        long e = event("PUBLISHED");
        LocalDateTime now = LocalDateTime.now();
        long s = session(e, "NOT_OPEN", now.plusDays(3), now.plusDays(3).plusHours(2), now.plusDays(1));

        scheduler.tick();

        assertThat(sessionStatus(s)).isEqualTo("NOT_OPEN");
    }

    @Test
    @DisplayName("已过散场时间 → ONSALE 变 ENDED，演出随之 ENDED")
    void finishesEndedSessionAndEvent() {
        long e = event("PUBLISHED");
        LocalDateTime now = LocalDateTime.now();
        long s = session(e, "ONSALE", now.minusDays(2), now.minusDays(2).plusHours(2), now.minusDays(10));

        scheduler.tick();

        assertThat(sessionStatus(s)).isEqualTo("ENDED");
        assertThat(eventStatus(e)).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("还有未散场场次 → 演出保持 PUBLISHED")
    void keepsEventPublishedWhenAnotherSessionRemains() {
        long e = event("PUBLISHED");
        LocalDateTime now = LocalDateTime.now();
        session(e, "ENDED", now.minusDays(3), now.minusDays(3).plusHours(2), now.minusDays(10));
        long future = session(e, "NOT_OPEN", now.plusDays(2), now.plusDays(2).plusHours(2), now.plusHours(5));

        scheduler.tick();

        assertThat(eventStatus(e)).isEqualTo("PUBLISHED");
        assertThat(sessionStatus(future)).isEqualTo("NOT_OPEN");
    }
}
