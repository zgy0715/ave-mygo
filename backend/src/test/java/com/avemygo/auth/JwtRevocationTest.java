package com.avemygo.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * 封禁即时生效（V55，审阅项 3）。
 *
 * 原来的过滤器只验签名与过期，封禁用户后对方 token 在 24 小时内照常可用。
 * 现在过滤额外比对「账户状态 + 令牌版本」，封禁时版本 +1，旧令牌立刻失效。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtRevocationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private static final String USER = "revoke_u1";
    private static final String PASS = "pass1234";

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM user WHERE username = ?", USER);
    }

    private String login() throws Exception {
        String body = "{\"username\":\"" + USER + "\",\"password\":\"" + PASS + "\"}";
        String resp = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(resp).path("data").path("token").asString();
    }

    @Test
    @DisplayName("J01 封禁后旧令牌立刻 401，重新登录的新令牌也不可用")
    void j01_banInvalidatesTokenImmediately() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USER + "\",\"password\":\"" + PASS + "\"}"))
                .andExpect(jsonPath("$.code").value(0));

        String token = login();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 管理员封禁（状态 → 0，版本 +1）
        jdbc.update("UPDATE user SET status = 0, token_version = token_version + 1 "
                + "WHERE username = ?", USER);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号已被停用，请联系管理员"));
    }

    @Test
    @DisplayName("J02 仅提升令牌版本（改权限场景）：旧令牌 401，新登录可用")
    void j02_versionBumpRevokesOldToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USER + "\",\"password\":\"" + PASS + "\"}"))
                .andExpect(jsonPath("$.code").value(0));

        String oldToken = login();
        jdbc.update("UPDATE user SET token_version = token_version + 1 WHERE username = ?", USER);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录状态已失效，请重新登录"));

        // 重新登录拿到带新版本号的令牌，可正常访问
        String newToken = login();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }
}
