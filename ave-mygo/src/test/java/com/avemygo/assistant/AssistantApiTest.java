package com.avemygo.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * AI 助手：接口结构 + 参数校验 + LLM 不可用时的规则降级。
 * 把 llm.base-url 指向必然拒绝连接的地址（127.0.0.1:9），保证测试确定性——
 * 否则本地开着 Ollama 时结果会随模型波动。
 */
@SpringBootTest(properties = "llm.base-url=http://127.0.0.1:9/v1/chat/completions")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantApiTest {

    @Autowired private MockMvc mvc;

    private String body(String message, String persona) {
        String p = persona == null ? "" : ",\"persona\":\"" + persona + "\"";
        return "{\"message\":\"" + message + "\"" + p + "}";
    }

    @Test
    @DisplayName("I01 LLM 不可用时降级：仍返回回复、意图与角色信息")
    void i01_fallback() throws Exception {
        mvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(body("站内一共有多少首歌", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reply").isNotEmpty())
                .andExpect(jsonPath("$.data.persona").value("小灯"))
                .andExpect(jsonPath("$.data.personaName").isNotEmpty());
    }

    @Test
    @DisplayName("I02 角色切换生效（sakiko）")
    void i02_persona() throws Exception {
        String resp = mvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(body("推荐几首歌", "sakiko")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.persona").value("祥子"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(resp).contains("personaName");
    }

    @Test
    @DisplayName("I03 空消息 / 超长消息 → 40001")
    void i03_validation() throws Exception {
        mvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(body("啊".repeat(600), null)))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("I04 匿名可用（不需要登录）")
    void i04_public() throws Exception {
        mvc.perform(post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(body("MyGO 有哪些成员", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
