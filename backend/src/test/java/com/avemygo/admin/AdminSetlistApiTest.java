package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 管理端歌单录入：全量替换语义 + 曲序 + 安可标记 + 三类校验 + 权限。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSetlistApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        jdbc.update("DELETE FROM setlist_item WHERE session_id IN (SELECT id FROM live_session WHERE name='[SL] 场次')");
        jdbc.update("DELETE FROM live_session WHERE name='[SL] 场次'");
        jdbc.update("DELETE FROM live_event WHERE title='[SL] 演出'");
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[SL] 演出',1,'场馆','PUBLISHED',0,NOW(),NOW())");
        Long eid = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'[SL] 场次',NOW(),NOW(),NOW(),'ONSALE',0,NOW(),NOW())", eid);
        sessionId = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM setlist_item WHERE session_id IN (SELECT id FROM live_session WHERE name='[SL] 场次')");
        jdbc.update("DELETE FROM live_session WHERE name='[SL] 场次'");
        jdbc.update("DELETE FROM live_event WHERE title='[SL] 演出'");
    }

    private String body(String itemsJson) {
        return "{\"items\":[" + itemsJson + "]}";
    }

    @Test
    @DisplayName("L01 PUT 全量替换：3 首（含安可），GET 按曲序返回标题")
    void l01_replace() throws Exception {
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"songId\":1,\"orderNo\":1},{\"songId\":2,\"orderNo\":2},"
                                + "{\"songId\":3,\"orderNo\":3,\"encore\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[2].encore").value(true))
                .andExpect(jsonPath("$.data[0].songTitle").isNotEmpty());

        mvc.perform(get("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].orderNo").value(1));
    }

    @Test
    @DisplayName("L02 再次 PUT 是替换而非追加：旧曲目被清空")
    void l02_replaceNotAppend() throws Exception {
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":1},{\"songId\":2}")));
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":3}")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].songId").value(3));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM setlist_item WHERE session_id=?", Integer.class, sessionId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("L03 空列表 = 清空歌单")
    void l03_clear() throws Exception {
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":1}")));
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("L04 校验：同曲重复 40001、曲序重复 40001、歌不存在 40401、场次不存在 40401")
    void l04_guards() throws Exception {
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"songId\":1,\"orderNo\":1},{\"songId\":1,\"orderNo\":2}")))
                .andExpect(jsonPath("$.code").value(40001));

        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"songId\":1,\"orderNo\":1},{\"songId\":2,\"orderNo\":1}")))
                .andExpect(jsonPath("$.code").value(40001));

        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":999999}")))
                .andExpect(jsonPath("$.code").value(40401));

        mvc.perform(put("/api/v1/admin/sessions/999999/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":1}")))
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("L05 曲序可不传：按提交顺序自动编号 1..n")
    void l05_autoOrder() throws Exception {
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":3},{\"songId\":1}")))
                .andExpect(jsonPath("$.data[0].orderNo").value(1))
                .andExpect(jsonPath("$.data[1].orderNo").value(2))
                .andExpect(jsonPath("$.data[0].songId").value(3));
    }

    @Test
    @DisplayName("L06 普通用户无权录入 → 403")
    void l06_forbidden() throws Exception {
        String userToken = "Bearer " + jwtService.generate(2L, "USER");
        mvc.perform(put("/api/v1/admin/sessions/" + sessionId + "/setlist").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("{\"songId\":1}")))
                .andExpect(status().isForbidden());
    }
}
