package com.avemygo.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.AuthService;
import com.avemygo.auth.JwtService;
import com.avemygo.auth.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 开售提醒：订阅幂等、取消幂等、列表带下次开售时间、定时任务置已提醒、权限与状态校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;
    @Autowired private ReminderService reminderService;
    @Autowired private JdbcTemplate jdbc;

    private String token;
    private Long userId;
    private Long futureEventId;
    private Long dueEventId;
    private Long draftEventId;

    @BeforeEach
    void setUp() {
        userId = authService.register(new RegisterRequest("rm_" + System.currentTimeMillis() % 100000, "pass1234", null));
        token = "Bearer " + jwtService.generate(userId, "USER");
        cleanup();

        futureEventId = insertEvent("[RM] 未来开售", "PUBLISHED");
        insertSession(futureEventId, "DATE_ADD(NOW(), INTERVAL 5 DAY)");
        dueEventId = insertEvent("[RM] 已到开售", "PUBLISHED");
        insertSession(dueEventId, "DATE_SUB(NOW(), INTERVAL 1 HOUR)");
        draftEventId = insertEvent("[RM] 草稿", "DRAFT");
        insertSession(draftEventId, "DATE_ADD(NOW(), INTERVAL 5 DAY)");
    }

    private Long insertEvent(String title, String status) {
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,1,'提醒场馆',?,0,NOW(),NOW())", title, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
    }

    private void insertSession(Long eventId, String saleStartExpr) {
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,?,DATE_ADD(NOW(),INTERVAL 10 DAY),DATE_ADD(NOW(),INTERVAL 10 DAY)," + saleStartExpr
                + ",'NOT_OPEN',0,NOW(),NOW())", eventId, "[RM] 场次");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM event_reminder WHERE event_id IN (SELECT id FROM live_event WHERE title LIKE '[RM]%')");
        jdbc.update("DELETE FROM live_session WHERE event_id IN (SELECT id FROM live_event WHERE title LIKE '[RM]%')");
        jdbc.update("DELETE FROM live_event WHERE title LIKE '[RM]%'");
        jdbc.update("DELETE FROM user WHERE username LIKE 'rm\\_%'");
    }

    @Test
    @DisplayName("M01 订阅幂等 + 列表带下次开售时间")
    void m01_subscribe() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/events/" + futureEventId + "/reminder").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.eventTitle").value("[RM] 未来开售"))
                    .andExpect(jsonPath("$.data.saleStartTime").isNotEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_reminder WHERE event_id=?",
                Integer.class, futureEventId)).isEqualTo(1);

        mvc.perform(get("/api/v1/reminders").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].notified").value(false));
    }

    @Test
    @DisplayName("M02 取消订阅幂等：列表为空，重复取消也不报错")
    void m02_unsubscribe() throws Exception {
        mvc.perform(post("/api/v1/events/" + futureEventId + "/reminder").header("Authorization", token));
        mvc.perform(delete("/api/v1/events/" + futureEventId + "/reminder").header("Authorization", token))
                .andExpect(jsonPath("$.data.removed").value(true));
        mvc.perform(delete("/api/v1/events/" + futureEventId + "/reminder").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/reminders").header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("M03 定时任务：已到开售时间的订阅被置为已提醒，未到点的不动")
    void m03_schedulerMarksNotified() throws Exception {
        mvc.perform(post("/api/v1/events/" + dueEventId + "/reminder").header("Authorization", token));
        mvc.perform(post("/api/v1/events/" + futureEventId + "/reminder").header("Authorization", token));

        reminderService.tick();

        mvc.perform(get("/api/v1/reminders").header("Authorization", token))
                .andExpect(jsonPath("$.data[?(@.eventId == " + dueEventId + ")].notified").value(true))
                .andExpect(jsonPath("$.data[?(@.eventId == " + futureEventId + ")].notified").value(false));
    }

    @Test
    @DisplayName("M04 校验：草稿演出 40401、不存在 40401、未登录 401")
    void m04_guards() throws Exception {
        mvc.perform(post("/api/v1/events/" + draftEventId + "/reminder").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(post("/api/v1/events/999999/reminder").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(post("/api/v1/events/" + futureEventId + "/reminder"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reminders")).andExpect(status().isUnauthorized());
    }
}
