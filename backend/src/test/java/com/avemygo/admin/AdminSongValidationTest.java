package com.avemygo.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.JwtService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
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
 * 管理端歌曲写入的参数校验：空标题、超长、非法乐队、非法状态必须被拦在入口层。
 * 修复前 AdminDtos 零校验注解（null 标题也能入库）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSongValidationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;

    private String token;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        token = "Bearer " + jwtService.generate(1L, "ADMIN");
        jdbc.update("DELETE FROM song WHERE title LIKE '[TEST]%'");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM song WHERE title LIKE '[TEST]%'");
    }

    private JsonNode postBody(String url, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(post(url)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("S01 空标题 → 40001，不入库")
    void blankTitle() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "   ");
        body.put("bandId", 1);

        mvc.perform(post("/api/v1/admin/songs")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM song WHERE title LIKE '[TEST]%'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("S02 乐队不存在 → 40001")
    void unknownBand() throws Exception {
        JsonNode resp = postBody("/api/v1/admin/songs", Map.of("title", "[TEST] 幽灵乐队", "bandId", 999));
        org.assertj.core.api.Assertions.assertThat(resp.path("code").asInt()).isEqualTo(40001);
    }

    @Test
    @DisplayName("S03 合法创建成功，非法状态 2 → 40001")
    void statusValidation() throws Exception {
        JsonNode ok = postBody("/api/v1/admin/songs", Map.of(
                "title", "[TEST] 校验歌曲", "bandId", 1, "status", 1));
        org.assertj.core.api.Assertions.assertThat(ok.path("code").asInt()).isZero();
        long id = ok.path("data").path("id").asLong();

        mvc.perform(patch("/api/v1/admin/songs/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
