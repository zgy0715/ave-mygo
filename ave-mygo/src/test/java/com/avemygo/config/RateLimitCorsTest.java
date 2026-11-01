package com.avemygo.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 限流响应必须带 CORS 头（2026-11-01 修复的回归测试）。
 *
 * 背景：全量 E2E 时报「CORS policy: No 'Access-Control-Allow-Origin'」，
 * 排查半天以为是跨域配置坏了——真因是**限流 429 的响应没带跨域头**，
 * 浏览器把所有无 ACAO 的响应当成 CORS 失败，把 429 掩盖成"跨域错误"。
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.default-limit=2",          // 第 3 次即触发限流
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitCorsTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("R01 限流 429 响应带 Access-Control-Allow-Origin 与 Retry-After")
    void rateLimitedResponseKeepsCorsHeaders() throws Exception {
        String origin = "http://localhost:5173";

        // 前两次放行
        mvc.perform(get("/api/v1/bands").header("Origin", origin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/bands").header("Origin", origin)).andExpect(status().isOk());

        // 第三次被限流，但必须带跨域头（否则浏览器只会报 CORS）
        mvc.perform(get("/api/v1/bands").header("Origin", origin))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().exists("Retry-After"));
    }
}
