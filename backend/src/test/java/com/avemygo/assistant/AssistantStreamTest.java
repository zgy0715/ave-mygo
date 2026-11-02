package com.avemygo.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SSE 流式问答：验证事件格式（多条 delta + 一条 done）、顺序、内容非空、参数校验。
 * llm.base-url 指向必然拒绝连接的地址，保证走规则引擎、结果确定。
 * 重复推送完整回复：把分片拼起来应等于 /chat 的 reply（不丢字、不重复）。
 */
@SpringBootTest(properties = "llm.base-url=http://127.0.0.1:9/v1/chat/completions")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantStreamTest {

    @Autowired private MockMvc mvc;

    private MvcResult stream(String body) throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn();
    }

    @Test
    @DisplayName("SS01 流式输出：多条 delta + 结尾 done，内容拼接非空")
    void ss01_stream() throws Exception {
        MvcResult r = stream("{\"message\":\"站内一共有多少首歌\"}");
        String text = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(r.getResponse().getContentType()).contains("text/event-stream");
        long deltaCount = text.lines().filter(l -> l.contains("\"delta\"")).count();
        assertThat(deltaCount).as("应拆成多段推送").isGreaterThan(1);
        assertThat(text).contains("\"done\":true").contains("\"intent\"").contains("\"personaName\"");
        assertThat(text).doesNotContain("\"delta\":\"\"");
    }

    @Test
    @DisplayName("SS02 拼接后的文本与一次性回复一致（不丢字、不重复）")
    void ss02_concatenationMatches() throws Exception {
        String reply = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(mvc.perform(post("/api/v1/assistant/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"推荐三首歌\"}"))
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("reply").asString();

        String streamed = stream("{\"message\":\"推荐三首歌\"}")
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        StringBuilder joined = new StringBuilder();
        for (String line : streamed.split("\n")) {
            if (!line.startsWith("data:")) continue;
            String json = line.substring(5).trim();
            if (!json.startsWith("{")) continue;
            var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
            if (node.has("delta")) joined.append(node.path("delta").asString());
        }
        assertThat(joined.toString()).isEqualTo(reply);
    }

    @Test
    @DisplayName("SS03 空消息 → 400（@Valid 在流式接口同样生效）")
    void ss03_validation() throws Exception {
        mvc.perform(post("/api/v1/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("40001")));
    }
}
