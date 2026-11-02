package com.avemygo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * 认证接口：注册参数校验、登录失败不区分用户不存在/密码错、/me 结构、未登录 401。
 * 用 MockMvc 走真实 HTTP 层，验证 @Valid 与全局异常处理确实生效。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM user WHERE username LIKE 'auth_%'");
    }

    private String json(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("A01 密码不含数字 / 用户名过短 / 用户名含非法字符 → 40001")
    void a01_registerValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("auth_ok", "onlyletters")))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ab", "pass1234")))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("auth 空格", "pass1234")))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("A02 注册成功 → 登录拿到 token → /me 返回 userId 与 ROLE_USER")
    void a02_registerLoginMe() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("auth_normal", "pass1234")))
                .andExpect(jsonPath("$.code").value(0));

        String resp = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("auth_normal", "pass1234")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String token = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(resp).path("data").path("token").asString();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.authorities").value(org.hamcrest.Matchers.containsString("ROLE_USER")));
    }

    @Test
    @DisplayName("A03 密码错误与用户不存在返回同一错误码（不泄露账号是否存在）")
    void a03_loginIndistinguishable() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("auth_real", "pass1234")));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("auth_real", "wrong999")))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("auth_nobody", "pass1234")))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("A04 未携带 token 访问 /me → 401")
    void a04_unauthorized() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A05 非法 token → 401（不 500）")
    void a05_badToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
