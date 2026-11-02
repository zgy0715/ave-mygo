package com.avemygo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 请求追踪号：无上游 ID 时自动生成 8 位；有上游 ID 时透传，保证跨服务链路连续。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("响应头自动带 8 位 X-Request-Id，且两次请求不重复")
    void autoGenerate() throws Exception {
        MvcResult first = mvc.perform(get("/api/v1/songs")).andReturn();
        MvcResult second = mvc.perform(get("/api/v1/songs")).andReturn();

        String id1 = first.getResponse().getHeader(RequestIdFilter.HEADER);
        String id2 = second.getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(id1).hasSize(8);
        assertThat(id2).hasSize(8).isNotEqualTo(id1);
    }

    @Test
    @DisplayName("透传上游 X-Request-Id")
    void passThrough() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/songs").header(RequestIdFilter.HEADER, "trace-1234"))
                .andReturn();
        assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-1234");
    }
}
