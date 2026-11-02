package com.avemygo.comment;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * 评论接口：发表/分页/越权删除/软删除/上架校验/公开可见。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();
    private String tokenA;
    private String tokenB;
    private String adminToken;
    private Long userA;
    private Long userB;

    @BeforeEach
    void setUp() {
        userA = authService.register(new RegisterRequest("cm_a_" + System.currentTimeMillis() % 100000, "pass1234", "小A"));
        userB = authService.register(new RegisterRequest("cm_b_" + System.currentTimeMillis() % 100000, "pass1234", "小B"));
        tokenA = "Bearer " + jwtService.generate(userA, "USER");
        tokenB = "Bearer " + jwtService.generate(userB, "USER");
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        jdbc.update("DELETE FROM comment WHERE song_id = 1");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM comment WHERE song_id = 1");
        jdbc.update("DELETE FROM user WHERE username LIKE 'cm\\_%'");
    }

    private long create(String token, long songId, String content) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/comments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":" + songId + ",\"content\":\"" + content + "\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    @Test
    @DisplayName("C01 发表评论 → 返回昵称/内容/own=true；游客可读且 own=false")
    void c01_createAndList() throws Exception {
        long id = create(tokenA, 1, "这首现场太强了");
        assertThat(id).isPositive();

        mvc.perform(get("/api/v1/songs/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].nickname").value("小A"))
                .andExpect(jsonPath("$.data.content[0].own").value(false));   // 匿名视角

        mvc.perform(get("/api/v1/songs/1/comments").header("Authorization", tokenA))
                .andExpect(jsonPath("$.data.content[0].own").value(true));
    }

    @Test
    @DisplayName("C02 分页：按时间倒序，pageSize 生效")
    void c02_paging() throws Exception {
        for (int i = 1; i <= 3; i++) create(tokenA, 1, "第" + i + "条");
        mvc.perform(get("/api/v1/songs/1/comments").param("page", "1").param("pageSize", "2"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].content").value("第3条"))   // 最新在前
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @DisplayName("C03 删除自己的评论 → 列表消失（软删除，库里仍留痕）")
    void c03_deleteOwn() throws Exception {
        long id = create(tokenA, 1, "待删除");
        mvc.perform(delete("/api/v1/comments/" + id).header("Authorization", tokenA))
                .andExpect(jsonPath("$.data.deleted").value(true));

        mvc.perform(get("/api/v1/songs/1/comments"))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM comment WHERE id=?", Byte.class, id))
                .isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("C04 删除别人的评论 → 40301；ADMIN 可删")
    void c04_deleteOthers() throws Exception {
        long id = create(tokenA, 1, "别人的评论");
        mvc.perform(delete("/api/v1/comments/" + id).header("Authorization", tokenB))
                .andExpect(jsonPath("$.code").value(40301));
        mvc.perform(delete("/api/v1/comments/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("C05 校验：空内容 40001、超 500 字 40001、歌曲不存在 40401、未登录发表 401")
    void c05_guards() throws Exception {
        mvc.perform(post("/api/v1/comments").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songId\":1,\"content\":\"   \"}"))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/v1/comments").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":1,\"content\":\"" + "啊".repeat(501) + "\"}"))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/v1/comments").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songId\":999999,\"content\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(post("/api/v1/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":1,\"content\":\"未登录\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("C06 下架歌曲不可评论（40401）")
    void c06_offlineSong() throws Exception {
        jdbc.update("INSERT INTO song(title,band_id,status,is_deleted,created_at,updated_at) "
                + "VALUES('[CM] 下架曲',1,0,0,NOW(),NOW())");
        Long sid = jdbc.queryForObject("SELECT MAX(id) FROM song", Long.class);
        mvc.perform(post("/api/v1/comments").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":" + sid + ",\"content\":\"下架也评论\"}"))
                .andExpect(jsonPath("$.code").value(40401));
        jdbc.update("DELETE FROM song WHERE id=?", sid);
    }
}
