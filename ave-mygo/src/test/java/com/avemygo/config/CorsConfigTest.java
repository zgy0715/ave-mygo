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
 * CORS 白名单：开发站(5173)与生产预览站(4173)都必须放行，其他来源拒绝。
 * 回归背景：曾只放行 5173，导致 scripts/start.bat 起的前端（4173）全部 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("预览站 4173 来源 → 返回对应 Access-Control-Allow-Origin")
    void previewOriginAllowed() throws Exception {
        mvc.perform(get("/api/v1/songs").header("Origin", "http://localhost:4173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4173"));
    }

    @Test
    @DisplayName("开发站 5173 来源 → 放行")
    void devOriginAllowed() throws Exception {
        mvc.perform(get("/api/v1/songs").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("未登记来源 → 403（白名单不是通配符）")
    void unknownOriginRejected() throws Exception {
        mvc.perform(get("/api/v1/songs").header("Origin", "http://evil.example.com"))
                .andExpect(status().isForbidden());
    }
}
