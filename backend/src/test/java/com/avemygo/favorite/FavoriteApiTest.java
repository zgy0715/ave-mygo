package com.avemygo.favorite;

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

/**
 * 收藏接口：多态目标（歌曲/演出）、幂等添加/取消、目标校验、登录隔离。
 * 歌曲用 Flyway 种子里的真实数据（id=1），演出用临时数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    private String token;
    private Long userId;
    private Long eventId;

    @BeforeEach
    void setUp() {
        String username = "fav_" + System.currentTimeMillis() % 1000000;
        userId = authService.register(new RegisterRequest(username, "pass1234", null));
        token = "Bearer " + jwtService.generate(userId, "USER");

        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[TEST] 收藏演出',1,'收藏场馆','PUBLISHED',0,NOW(),NOW())");
        eventId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("DELETE FROM favorite WHERE user_id = ?", userId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM favorite WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM live_event WHERE title='[TEST] 收藏演出'");
        jdbc.update("DELETE FROM user WHERE username LIKE 'fav_%'");
    }

    @Test
    @DisplayName("F01 收藏歌曲 → 列表带出标题/封面/乐队名")
    void f01_addSong() throws Exception {
        mvc.perform(post("/api/v1/favorites").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"SONG\",\"targetId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").isNotEmpty());

        mvc.perform(get("/api/v1/favorites").param("targetType", "SONG").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].targetType").value("SONG"))
                .andExpect(jsonPath("$.data[0].coverUrl").isNotEmpty())
                .andExpect(jsonPath("$.data[0].extra").isNotEmpty());
    }

    @Test
    @DisplayName("F02 重复收藏幂等：列表仍只有 1 条")
    void f02_idempotentAdd() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/favorites").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetType\":\"SONG\",\"targetId\":1}"))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mvc.perform(get("/api/v1/favorites").param("targetType", "SONG").header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("F03 收藏演出 → extra 为场馆")
    void f03_addEvent() throws Exception {
        mvc.perform(post("/api/v1/favorites").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"EVENT\",\"targetId\":" + eventId + "}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/favorites").param("targetType", "EVENT").header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("[TEST] 收藏演出"))
                .andExpect(jsonPath("$.data[0].extra").value("收藏场馆"));
    }

    @Test
    @DisplayName("F04 目标不存在 → 40401；类型非法 → 40001")
    void f04_guards() throws Exception {
        mvc.perform(post("/api/v1/favorites").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"SONG\",\"targetId\":999999}"))
                .andExpect(jsonPath("$.code").value(40401));

        mvc.perform(post("/api/v1/favorites").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"PLAYLIST\",\"targetId\":1}"))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("F05 取消收藏后列表为空，重复取消不报错")
    void f05_remove() throws Exception {
        mvc.perform(post("/api/v1/favorites").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"SONG\",\"targetId\":1}"));

        mvc.perform(delete("/api/v1/favorites/SONG/1").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/v1/favorites/SONG/1").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/favorites").param("targetType", "SONG").header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("F06 未登录访问收藏 → 401")
    void f06_requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/favorites")).andExpect(status().isUnauthorized());
    }
}
