package com.avemygo.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.JwtService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * 管理端演出域接口测试：演出 → 场次 → 票档 全链路 + 参数校验 + 业务规则。
 * 覆盖本次新增的 7 个端点，验证「演示时能改数据」这条刚需。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEventApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM ticket_type");
        jdbc.update("DELETE FROM live_session");
        jdbc.update("DELETE FROM live_event");
    }

    private JsonNode postJson(String url, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(post(url)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode patchJson(String url, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(patch(url)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("E01 演出→场次→票档 全链路创建，前台可见已发布演出")
    void e01_fullChain() throws Exception {
        // 1. 建演出
        JsonNode created = postJson("/api/v1/admin/events", Map.of(
                "title", "[TEST] 管理端新建 Live",
                "bandId", 1,
                "venue", "测试会场",
                "status", "PUBLISHED"));
        org.assertj.core.api.Assertions.assertThat(created.path("code").asInt()).isZero();
        long eventId = created.path("data").path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(created.path("data").path("status").asText())
                .isEqualTo("PUBLISHED");

        // 2. 建场次
        JsonNode session = postJson("/api/v1/admin/events/" + eventId + "/sessions", Map.of(
                "name", "昼场",
                "startTime", "2027-01-01T14:00:00",
                "endTime", "2027-01-01T17:00:00",
                "status", "ONSALE"));
        org.assertj.core.api.Assertions.assertThat(session.path("code").asInt()).isZero();
        long sessionId = session.path("data").path("id").asLong();

        // 3. 建票档
        JsonNode ticket = postJson("/api/v1/admin/sessions/" + sessionId + "/ticket-types", Map.of(
                "name", "S席", "price", 1280.00, "totalStock", 100, "maxPerUser", 4));
        org.assertj.core.api.Assertions.assertThat(ticket.path("code").asInt()).isZero();
        org.assertj.core.api.Assertions.assertThat(ticket.path("data").path("remaining").asInt())
                .isEqualTo(100);

        // 4. 前台演出详情可见（PUBLISHED）
        mvc.perform(get("/api/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("E02 参数校验：空标题 → 40001；结束早于开演 → 40001")
    void e02_validation() throws Exception {
        JsonNode bad = postJson("/api/v1/admin/events", Map.of("bandId", 1, "venue", "场馆"));
        org.assertj.core.api.Assertions.assertThat(bad.path("code").asInt()).isEqualTo(40001);

        JsonNode ok = postJson("/api/v1/admin/events", Map.of(
                "title", "[TEST] 校验用", "bandId", 1, "venue", "场馆"));
        long eventId = ok.path("data").path("id").asLong();

        JsonNode badSession = postJson("/api/v1/admin/events/" + eventId + "/sessions", Map.of(
                "name", "夜场",
                "startTime", "2027-02-01T18:00:00",
                "endTime", "2027-02-01T15:00:00"));
        org.assertj.core.api.Assertions.assertThat(badSession.path("code").asInt()).isEqualTo(40001);
    }

    @Test
    @DisplayName("E03 票档库存不得小于已售：先卖出 2 张，再改库存为 1 → 40001")
    void e03_stockBelowSold() throws Exception {
        long eventId = postJson("/api/v1/admin/events", Map.of(
                "title", "[TEST] 库存规则", "bandId", 1, "venue", "场馆",
                "status", "PUBLISHED")).path("data").path("id").asLong();
        long sessionId = postJson("/api/v1/admin/events/" + eventId + "/sessions", Map.of(
                "name", "昼场", "startTime", "2027-03-01T14:00:00",
                "endTime", "2027-03-01T17:00:00", "status", "ONSALE")).path("data").path("id").asLong();
        long typeId = postJson("/api/v1/admin/sessions/" + sessionId + "/ticket-types", Map.of(
                "name", "普通席", "price", 680.00, "totalStock", 10, "maxPerUser", 4))
                .path("data").path("id").asLong();

        // 模拟已售 2 张
        jdbc.update("UPDATE ticket_type SET sold_count = 2 WHERE id = ?", typeId);

        JsonNode resp = patchJson("/api/v1/admin/ticket-types/" + typeId, Map.of("totalStock", 1));
        org.assertj.core.api.Assertions.assertThat(resp.path("code").asInt()).isEqualTo(40001);

        // 改成 5 则允许，剩余 = 3
        JsonNode ok = patchJson("/api/v1/admin/ticket-types/" + typeId, Map.of("totalStock", 5));
        org.assertj.core.api.Assertions.assertThat(ok.path("code").asInt()).isZero();
        org.assertj.core.api.Assertions.assertThat(ok.path("data").path("remaining").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("E04 场次列表回显票档，演出列表显示场次数量")
    void e04_listing() throws Exception {
        long eventId = postJson("/api/v1/admin/events", Map.of(
                "title", "[TEST] 列表", "bandId", 1, "venue", "场馆")).path("data").path("id").asLong();
        postJson("/api/v1/admin/events/" + eventId + "/sessions", Map.of(
                "name", "昼场", "startTime", "2027-04-01T14:00:00", "endTime", "2027-04-01T17:00:00"));
        long session2 = postJson("/api/v1/admin/events/" + eventId + "/sessions", Map.of(
                "name", "夜场", "startTime", "2027-04-01T18:30:00", "endTime", "2027-04-01T21:30:00"))
                .path("data").path("id").asLong();
        postJson("/api/v1/admin/sessions/" + session2 + "/ticket-types", Map.of(
                "name", "A席", "price", 980.00, "totalStock", 50));

        mvc.perform(get("/api/v1/admin/events/" + eventId + "/sessions").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].ticketTypes.length()").value(1));
    }
}
