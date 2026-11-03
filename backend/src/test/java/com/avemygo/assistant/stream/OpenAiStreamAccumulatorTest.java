package com.avemygo.assistant.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 增量累加器单测（docs/19 P0-2）。
 * 云端 OpenAI 兼容协议的 tool_calls 按 index 分片，arguments 被切成多段——
 * 不拼接就只拿到半截 JSON，这是接云 API 必踩的坑，用固定样本锁死。
 */
class OpenAiStreamAccumulatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private OpenAiStreamAccumulator.Delta feed(OpenAiStreamAccumulator acc, String chunkJson)
            throws Exception {
        return acc.accept(mapper.readTree(chunkJson).path("choices").path(0).path("delta"));
    }

    @Test
    @DisplayName("云 API 分片：同 index 的 arguments 逐段累加成完整 JSON")
    void t01_incrementalToolCallChunks() throws Exception {
        OpenAiStreamAccumulator acc = new OpenAiStreamAccumulator();

        feed(acc, """
                {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",
                  "function":{"name":"list_events","arguments":""}}]}}]}
                """);
        feed(acc, """
                {"choices":[{"delta":{"tool_calls":[{"index":0,
                  "function":{"arguments":"{\\"limit\\":"}}]}}]}
                """);
        feed(acc, """
                {"choices":[{"delta":{"tool_calls":[{"index":0,
                  "function":{"arguments":"3}"}}]}}]}
                """);

        List<StreamLlm.ToolCall> calls = acc.toolCalls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).id()).isEqualTo("call_1");
        assertThat(calls.get(0).name()).isEqualTo("list_events");
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"limit\":3}");
    }

    @Test
    @DisplayName("Ollama 整块：无 index 也按一条拼出，行为与旧版一致")
    void t02_ollamaWholeBlockStillWorks() throws Exception {
        OpenAiStreamAccumulator acc = new OpenAiStreamAccumulator();
        feed(acc, """
                {"choices":[{"delta":{"tool_calls":[{"id":"call_9",
                  "function":{"name":"get_event","arguments":"{\\"eventId\\":2}"}}]}}]}
                """);

        List<StreamLlm.ToolCall> calls = acc.toolCalls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"eventId\":2}");
    }

    @Test
    @DisplayName("多条工具调用：不同 index 各自独立累加，顺序按 index")
    void t03_multipleToolCallsByIndex() throws Exception {
        OpenAiStreamAccumulator acc = new OpenAiStreamAccumulator();
        feed(acc, """
                {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"b",
                  "function":{"name":"second","arguments":"{}"}}]}}]}
                """);
        feed(acc, """
                {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a",
                  "function":{"name":"first","arguments":"{\\"x\\":1}"}}]}}]}
                """);

        List<StreamLlm.ToolCall> calls = acc.toolCalls();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).name()).isEqualTo("first");    // index 0 在前
        assertThat(calls.get(1).name()).isEqualTo("second");
    }

    @Test
    @DisplayName("正文增量按序拼接；reasoning 只在第一片报 true")
    void t04_contentAndThinking() throws Exception {
        OpenAiStreamAccumulator acc = new OpenAiStreamAccumulator();

        OpenAiStreamAccumulator.Delta d1 =
                feed(acc, "{\"choices\":[{\"delta\":{\"reasoning\":\"想\",\"content\":\"\"}}]}");
        OpenAiStreamAccumulator.Delta d2 =
                feed(acc, "{\"choices\":[{\"delta\":{\"reasoning\":\"考\",\"content\":\"你\"}}]}");
        OpenAiStreamAccumulator.Delta d3 =
                feed(acc, "{\"choices\":[{\"delta\":{\"content\":\"好\"}}]}");

        assertThat(d1.thinkingStarted()).isTrue();   // 只有第一片报
        assertThat(d2.thinkingStarted()).isFalse();
        assertThat(d2.contentPiece()).isEqualTo("你");
        assertThat(d3.contentPiece()).isEqualTo("好");
        assertThat(acc.content()).isEqualTo("你好");
    }
}
