package com.avemygo.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 生产加固后的配置生效验证（本类单独开限流，其余用例默认关闭）：
 *  - 登录接口第 11 次请求被 429 拦截
 *  - CORS 允许来源改为配置项（生产改域名不改代码）
 */
@SpringBootTest(properties = {"app.rate-limit.enabled=true",
        "app.cors.allowed-origins=http://localhost:5173,https://bandori.example.com"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HardeningConfigTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("H01 登录接口限流：第 11 次同 IP 请求 → 429 + 42901")
    void h01_rateLimit() throws Exception {
        String ip = "203.0.113.7";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody_x\",\"password\":\"pass1234\"}"))
                    .andExpect(status().isOk());   // 业务层返回 40101，HTTP 仍是 200
        }
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody_x\",\"password\":\"pass1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));

        // 换 IP 不受影响（限流按 IP 分桶）
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody_x\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H02 CORS 来源读配置：配置里的域名放行，未登记域名拒绝")
    void h02_corsFromConfig() throws Exception {
        mvc.perform(get("/api/v1/songs").header("Origin", "https://bandori.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://bandori.example.com"));

        mvc.perform(get("/api/v1/songs").header("Origin", "http://localhost:4173"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H03 普通接口限流阈值更宽：连续 30 次歌曲查询不被拦")
    void h03_defaultLimit() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(get("/api/v1/songs").header("X-Forwarded-For", "203.0.113.9"))
                    .andExpect(status().isOk());
        }
    }
}
