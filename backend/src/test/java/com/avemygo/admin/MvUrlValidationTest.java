package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.avemygo.admin.dto.AdminDtos.SongUpsert;
import com.avemygo.agent.AgentToolCall;
import com.avemygo.agent.AgentToolCallRepository;
import com.avemygo.auth.JwtService;
import com.avemygo.song.SongService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MvUrlValidationTest {

    private static final String ORIGINAL_MV = "https://www.bilibili.com/video/BV1test";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AdminService adminService;
    @Autowired private SongService songService;
    @Autowired private AgentToolCallRepository auditRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private String adminToken;
    private Long songId;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        songId = adminService.create(new SongUpsert("[TEST]MV链接校验", (short) 1,
                null, null, null, 180, null, null, ORIGINAL_MV, "BILIBILI", null, (byte) 1)).id();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "https://player.bilibili.com.evil.example/player.html?bvid=BV1test",
            "https://evil.example/player.bilibili.com?bvid=BV1test",
            "https://www.bilibili.com@evil.example/video/BV1test",
            "https://user@www.bilibili.com/video/BV1test",
            "https://www.bilibili.com:8443/video/BV1test",
            "/relative"
    })
    @DisplayName("管理端新增与修改拒绝不可信 MV 地址，保留原链接")
    void rejectsUnsafeCreateAndPatch(String mvUrl) throws Exception {
        mvc.perform(post("/api/v1/admin/songs").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "title", "[TEST]不可信MV", "bandId", 1, "mvUrl", mvUrl))))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(patch("/api/v1/admin/songs/{id}", songId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mvUrl", mvUrl))))
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(songService.detail(songId).mvUrl()).isEqualTo(ORIGINAL_MV);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://player.bilibili.com.evil.example/player.html?bvid=BV1test",
            "https://user@www.youtube.com/watch?v=test"
    })
    @DisplayName("人工确认不能绕过 MV 校验，失败操作保留审计且不改歌曲")
    void confirmationRejectsUnsafeMv(String mvUrl) throws Exception {
        String token = pendingMvUpdate(mvUrl);
        confirm(token, "FAILED");
        assertThat(songService.detail(songId).mvUrl()).isEqualTo(ORIGINAL_MV);
        mvc.perform(post("/api/v1/admin/agent/confirm").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", token, "approve", true))))
                .andExpect(jsonPath("$.code").value(40902));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://player.bilibili.com/player.html?bvid=BV1test",
            "http://www.bilibili.com/video/BV1test",
            "https://www.bilibili.com/bangumi/play/ep123",
            "https://www.youtube.com/watch?v=test",
            "https://youtu.be/test"
    })
    @DisplayName("管理端与确认工具均接受支持的平台地址")
    void acceptsSupportedMv(String mvUrl) throws Exception {
        mvc.perform(post("/api/v1/admin/songs").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "title", "[TEST]可信平台MV", "bandId", 1, "mvUrl", mvUrl))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mvUrl").value(mvUrl));
        confirm(pendingMvUpdate(mvUrl), "EXECUTED");
        assertThat(songService.detail(songId).mvUrl()).isEqualTo(mvUrl);
    }

    @Test
    @DisplayName("管理修改省略 MV 保持原值，空白 MV 明确清空")
    void patchKeepsOmittedMvAndClearsBlank() throws Exception {
        mvc.perform(patch("/api/v1/admin/songs/{id}", songId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(songService.detail(songId).mvUrl()).isEqualTo(ORIGINAL_MV);
        mvc.perform(patch("/api/v1/admin/songs/{id}", songId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mvUrl\":\"  \"}"))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(songService.detail(songId).mvUrl()).isNull();
    }

    @Test
    @DisplayName("MV 长度与数据库一致：255 可写，256 在写入前拒绝")
    void mvLengthMatchesStorageBoundary() throws Exception {
        String prefix = "https://www.youtube.com/watch?v=";
        String boundary = prefix + "a".repeat(255 - prefix.length());
        confirm(pendingMvUpdate(boundary), "EXECUTED");
        assertThat(songService.detail(songId).mvUrl()).isEqualTo(boundary);
        String oversized = boundary + "a";
        mvc.perform(post("/api/v1/admin/songs").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "title", "[TEST]超长MV", "bandId", 1, "mvUrl", oversized))))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(patch("/api/v1/admin/songs/{id}", songId).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mvUrl", oversized))))
                .andExpect(jsonPath("$.code").value(40001));
        confirm(pendingMvUpdate(oversized), "FAILED");
        assertThat(songService.detail(songId).mvUrl()).isEqualTo(boundary);
    }

    private String pendingMvUpdate(String mvUrl) {
        AgentToolCall call = new AgentToolCall();
        call.setConversationId("mv-validation-test");
        call.setUserId(1L);
        call.setToolName("update_song_mv");
        call.setRisk("WRITE");
        call.setArgumentsJson(mapper.writeValueAsString(Map.of("songId", songId, "mvUrl", mvUrl)));
        call.setStatus(AgentToolCall.PENDING);
        call.setToken(UUID.randomUUID().toString());
        return auditRepository.saveAndFlush(call).getToken();
    }

    private void confirm(String token, String expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/admin/agent/confirm").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", token, "approve", true))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(expectedStatus));
    }
}
