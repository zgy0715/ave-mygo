package com.avemygo.assistant.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 流式 LLM 的 HTTP 层测试（docs/19 P0-2）。
 *
 * 起一个本地假 LLM 服务（com.sun.net.httpserver），验证三件事：
 * 1. 配了 LLM_API_KEY → 请求带 Authorization: Bearer 头；
 * 2. 没配 Key → 不发 Authorization（Ollama 行为不变）；
 * 3. 云端未配 LLM_MODEL → 启动即报清晰错误（不去探 localhost:11434）。
 */
class OllamaStreamLlmHttpTest {

    private HttpServer server;
    private final AtomicReference<String> authHeader = new AtomicReference<>("");

    /** 假服务返回标准 OpenAI 增量分片：tool_calls 的 arguments 被切成两段 */
    private static final String[] SSE_CHUNKS = {
            "data: {\"choices\":[{\"delta\":{\"content\":\"查\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\","
                    + "\"function\":{\"name\":\"list_events\",\"arguments\":\"{\\\"limit\\\":\"}}]}}]}",
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                    + "\"function\":{\"arguments\":\"5}\"}}]}}]}",
            "data: [DONE]"
    };

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (String chunk : SSE_CHUNKS) {
                    os.write((chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private OllamaStreamLlm newLlm(String apiKey, String model) {
        OllamaStreamLlm llm = new OllamaStreamLlm();
        ReflectionTestUtils.setField(llm, "baseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
        ReflectionTestUtils.setField(llm, "model", model);
        ReflectionTestUtils.setField(llm, "apiKey", apiKey);
        return llm;
    }

    @Test
    @DisplayName("配 Key：带 Authorization 头 + 分片 arguments 拼成完整 JSON")
    void t01_apiKeySendsBearerAndAssemblesChunks() throws Exception {
        StreamLlm llm = newLlm("sk-test-123", "deepseek-chat");
        List<String> contents = new ArrayList<>();
        List<StreamLlm.ToolCall> calls = new ArrayList<>();

        StreamLlm.Turn turn = llm.stream(List.of(Map.of("role", "user", "content", "hi")),
                null, new StreamLlm.Listener() {
                    @Override public void onThinking() {}
                    @Override public void onContent(String delta) { contents.add(delta); }
                    @Override public void onToolCall(String id, String name, String args) {
                        calls.add(new StreamLlm.ToolCall(id, name, args));
                    }
                });

        assertThat(authHeader.get()).isEqualTo("Bearer sk-test-123");
        assertThat(turn.content()).isEqualTo("查");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"limit\":5}");
        assertThat(turn.toolCalls()).hasSize(1);
    }

    @Test
    @DisplayName("不配 Key（本地 Ollama）：不发 Authorization 头")
    void t02_noApiKeyNoAuthHeader() throws Exception {
        StreamLlm llm = newLlm("", "qwen3:8b");

        llm.stream(List.of(Map.of("role", "user", "content", "hi")), null,
                new StreamLlm.Listener() {
                    @Override public void onThinking() {}
                    @Override public void onContent(String delta) {}
                    @Override public void onToolCall(String id, String name, String args) {}
                });

        assertThat(authHeader.get()).isNull();
    }

    @Test
    @DisplayName("云端未配模型：报错且指向 LLM_MODEL，而不是去探测 localhost")
    void t03_cloudRequiresExplicitModel() {
        OllamaStreamLlm llm = newLlm("sk-test-123", "");
        ReflectionTestUtils.setField(llm, "baseUrl", "https://api.deepseek.com/v1/chat/completions");

        assertThatThrownBy(() -> llm.stream(List.of(), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_MODEL");
    }
}
