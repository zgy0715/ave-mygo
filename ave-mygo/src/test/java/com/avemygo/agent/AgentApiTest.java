package com.avemygo.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.JwtService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 运营层测试（docs/10 §11）：工具注册表自检、循环、写操作门控、审计、权限。
 * LLM 用 @MockitoBean 脚本化替代，不需要真实 Ollama。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentToolRegistry registry;
    @Autowired private AgentToolCallRepository auditRepository;

    @MockitoBean private AgentLlm agentLlm;

    private final ObjectMapper mapper = new ObjectMapper();
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        userToken = "Bearer " + jwtService.generate(999L, "USER");
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM agent_tool_calls");
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM setlist_item");
        jdbc.update("DELETE FROM ticket_type");
        jdbc.update("DELETE FROM live_session");
        jdbc.update("DELETE FROM live_event");
    }

    // ---------------- 基础：注册表 ----------------

    @Test
    @DisplayName("A01 注册表：8 个工具、名称唯一、Schema 合法、写工具带说明")
    void a01_registry() {
        assertThat(registry.size()).isEqualTo(8);
        List<Map<String, Object>> schemas = registry.schemas();
        assertThat(schemas).hasSize(8);
        assertThat(schemas.stream().map(s -> ((Map<?, ?>) s.get("function")).get("name")).distinct().count())
                .isEqualTo(8);
        for (Map<String, Object> s : schemas) {
            Map<?, ?> fn = (Map<?, ?>) s.get("function");
            Map<?, ?> params = (Map<?, ?>) fn.get("parameters");
            assertThat(params.get("type")).isEqualTo("object");
        }
        // 写工具都在门控名单里，且都有说明
        for (String name : List.of("update_song_mv", "set_ticket_status", "delete_session", "delete_event")) {
            AgentTool t = registry.get(name);
            assertThat(t).isNotNull();
            assertThat(t.isWrite()).isTrue();
            assertThat(t.description()).isNotBlank();
        }
        assertThat(registry.get("drop_database")).isNull();
    }

    // ---------------- 只读循环 ----------------

    @Test
    @DisplayName("A03 只读工具链路：调用 → 回填 → 文本结论，审计 EXECUTED")
    void a03_readOnlyLoop() throws Exception {
        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                                new AgentLlm.ToolCall("c1", "search_songs", "{\"keyword\":\"迷星叫\"}"))),
                        new AgentLlm.Turn("找到《迷星叫》1 首。", List.of()));

        mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"找一下迷星叫\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("reply"))
                .andExpect(jsonPath("$.data.content").value("找到《迷星叫》1 首。"));

        var rows = auditRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getToolName()).isEqualTo("search_songs");
        assertThat(rows.get(0).getStatus()).isEqualTo(AgentToolCall.EXECUTED);
    }

    @Test
    @DisplayName("A02 未知工具：不执行、错误回填、循环继续")
    void a02_unknownTool() throws Exception {
        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                                new AgentLlm.ToolCall("c1", "drop_database", "{}"))),
                        new AgentLlm.Turn("我没法执行这个操作。", List.of()));

        mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删库\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("reply"));

        assertThat(auditRepository.findAll())
                .anyMatch(r -> "drop_database".equals(r.getToolName())
                        && AgentToolCall.FAILED.equals(r.getStatus()));
    }

    @Test
    @DisplayName("A11 参数幻觉：只读工具缺必填参数 → 错误回填、不崩溃")
    void a11_missingParam() throws Exception {
        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                                new AgentLlm.ToolCall("c1", "get_song", "{}"))),
                        new AgentLlm.Turn("需要一个歌曲 id。", List.of()));

        mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"查这首歌\"}"))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(auditRepository.findAll())
                .anyMatch(r -> "get_song".equals(r.getToolName())
                        && AgentToolCall.FAILED.equals(r.getStatus()));
    }

    @Test
    @DisplayName("A10 轮数上限：LLM 永远要调工具 → 返回步数超限，不死循环")
    void a10_maxSteps() throws Exception {
        AgentLlm.Turn loop = new AgentLlm.Turn(null, List.of(
                new AgentLlm.ToolCall("c1", "search_songs", "{\"keyword\":\"x\"}")));
        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(loop, loop, loop, loop, loop, loop, loop, loop);

        mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"无限循环\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("reply"))
                .andExpect(jsonPath("$.data.content").value(
                        org.hamcrest.Matchers.containsString("步数超过上限")));
    }

    // ---------------- 写操作门控 ----------------

    @Test
    @DisplayName("A04/A05 写操作先门控（数据未动），确认后才执行")
    void a04_a05_gateAndApprove() throws Exception {
        long eventId = createEventWithSession();
        long sessionId = jdbc.queryForObject(
                "SELECT id FROM live_session WHERE event_id = ? LIMIT 1", Long.class, eventId);

        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                        new AgentLlm.ToolCall("c1", "delete_session", "{\"sessionId\":" + sessionId + "}"))));

        MvcResult res = mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删掉这个场次\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("pending_confirmation"))
                .andExpect(jsonPath("$.data.toolName").value("delete_session"))
                .andReturn();

        JsonNode body = mapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        // 门控生效：数据完全没动，审计是 PENDING
        assertThat(isSessionDeleted(sessionId)).isFalse();
        assertThat(auditRepository.findByToken(token).orElseThrow().getStatus())
                .isEqualTo(AgentToolCall.PENDING);

        // 确认执行
        mvc.perform(post("/api/v1/admin/agent/confirm")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"approve\":true}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));

        assertThat(isSessionDeleted(sessionId)).isTrue();
        assertThat(auditRepository.findByToken(token).orElseThrow().getStatus())
                .isEqualTo(AgentToolCall.EXECUTED);
    }

    @Test
    @DisplayName("A06 确认拒绝：数据不变，审计 REJECTED")
    void a06_reject() throws Exception {
        long eventId = createEventWithSession();
        long sessionId = jdbc.queryForObject(
                "SELECT id FROM live_session WHERE event_id = ? LIMIT 1", Long.class, eventId);

        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                        new AgentLlm.ToolCall("c1", "delete_session", "{\"sessionId\":" + sessionId + "}"))));

        MvcResult res = mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删掉这个场次\"}"))
                .andReturn();
        String token = mapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("token").asText();

        mvc.perform(post("/api/v1/admin/agent/confirm")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"approve\":false}"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(isSessionDeleted(sessionId)).isFalse();
        assertThat(auditRepository.findByToken(token).orElseThrow().getStatus())
                .isEqualTo(AgentToolCall.REJECTED);
    }

    @Test
    @DisplayName("A07 重复确认：同一 token 第二次 → 40902")
    void a07_duplicateConfirm() throws Exception {
        long eventId = createEventWithSession();
        long sessionId = jdbc.queryForObject(
                "SELECT id FROM live_session WHERE event_id = ? LIMIT 1", Long.class, eventId);

        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                        new AgentLlm.ToolCall("c1", "delete_session", "{\"sessionId\":" + sessionId + "}"))));

        MvcResult res = mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删掉这个场次\"}"))
                .andReturn();
        String token = mapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("token").asText();

        String body = "{\"token\":\"" + token + "\",\"approve\":true}";
        mvc.perform(post("/api/v1/admin/agent/confirm").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));
        mvc.perform(post("/api/v1/admin/agent/confirm").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    @DisplayName("A08 非本人确认：他人的 token → 40902，且数据不变")
    void a08_notOwner() throws Exception {
        long eventId = createEventWithSession();
        long sessionId = jdbc.queryForObject(
                "SELECT id FROM live_session WHERE event_id = ? LIMIT 1", Long.class, eventId);

        Mockito.when(agentLlm.chat(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new AgentLlm.Turn(null, List.of(
                        new AgentLlm.ToolCall("c1", "delete_session", "{\"sessionId\":" + sessionId + "}"))));

        MvcResult res = mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删掉这个场次\"}"))
                .andReturn();
        String token = mapper.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("token").asText();

        // 另一个管理员（userId=2）来确认
        String otherAdmin = "Bearer " + jwtService.generate(2L, "ADMIN");
        mvc.perform(post("/api/v1/admin/agent/confirm").header("Authorization", otherAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"approve\":true}"))
                .andExpect(jsonPath("$.code").value(40902));

        assertThat(isSessionDeleted(sessionId)).isFalse();
    }

    @Test
    @DisplayName("A09 越权：普通用户调 Agent 接口 → 403")
    void a09_forbidden() throws Exception {
        mvc.perform(post("/api/v1/admin/agent/chat")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"删掉所有演出\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------- 帮助方法 ----------------

    private boolean isSessionDeleted(long sessionId) {
        Integer deleted = jdbc.queryForObject(
                "SELECT is_deleted FROM live_session WHERE id = ?", Integer.class, sessionId);
        return deleted != null && deleted == 1;
    }

    private long createEventWithSession() throws Exception {
        MvcResult ev = mvc.perform(post("/api/v1/admin/events").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"[TEST] Agent 用例\",\"bandId\":1,\"venue\":\"场馆\",\"status\":\"PUBLISHED\"}"))
                .andReturn();
        long eventId = mapper.readTree(ev.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        mvc.perform(post("/api/v1/admin/events/" + eventId + "/sessions").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"昼场\",\"startTime\":\"2027-11-01T14:00:00\",\"endTime\":\"2027-11-01T17:00:00\",\"status\":\"ONSALE\"}"))
                .andReturn();
        return eventId;
    }
}
