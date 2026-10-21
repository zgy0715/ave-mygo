package com.avemygo.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 就绪探针：数据库可用 → 200/UP；匿名可访问（编排系统不会带 token）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadinessApiTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("R01 /ready 匿名可访问，DB 正常时返回 UP")
    void r01_ready() throws Exception {
        mvc.perform(get("/api/v1/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.db").value("UP"));
    }

    @Test
    @DisplayName("R02 /health 仍是存活探针（不查 DB）")
    void r02_health() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
