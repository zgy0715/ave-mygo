package com.avemygo.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Agent 端 LLM（非流式）的 HTTP 行为测试（2026-11-05）。
 *
 * 背景：非流式端原先没发 Authorization，导致「v2 流式失败 → 前端降级 v1」
 * 这条兜底链路在云端直接 401（云上没有 Ollama，v1 也走云 API）。
 * 这里锁死三件事：配 Key 必发 Bearer、不配 Key 不发、云端未配模型报错。
 */
class OllamaAgentLlmHttpTest {

    private HttpServer server;
    private final AtomicReference<String> authHeader = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"好的\","
                    + "\"tool_calls\":[{\"id\":\"c1\",\"function\":{\"name\":\"list_events\","
                    + "\"arguments\":\"{\\\"limit\\\":3}\"}}]}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private OllamaAgentLlm newLlm(String apiKey, String model) {
        OllamaAgentLlm llm = new OllamaAgentLlm();
        ReflectionTestUtils.setField(llm, "baseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
        ReflectionTestUtils.setField(llm, "model", model);
        ReflectionTestUtils.setField(llm, "apiKey", apiKey);
        ReflectionTestUtils.setField(llm, "warmupEnabled", false);
        return llm;
    }

    @Test
    @DisplayName("配 Key：请求带 Bearer 头（修复 v1 兜底在云端 401）")
    void t01_apiKeySendsBearer() throws Exception {
        AgentLlm llm = newLlm("sk-agent-test", "deepseek-chat");

        AgentLlm.Turn turn = llm.chat(List.of(Map.of("role", "user", "content", "hi")), List.of());

        assertThat(authHeader.get()).isEqualTo("Bearer sk-agent-test");
        assertThat(turn.content()).isEqualTo("好的");
        assertThat(turn.toolCalls()).hasSize(1);
        assertThat(turn.toolCalls().get(0).argumentsJson()).isEqualTo("{\"limit\":3}");
    }

    @Test
    @DisplayName("不配 Key（本地 Ollama）：不发鉴权头")
    void t02_noApiKeyNoAuthHeader() throws Exception {
        AgentLlm llm = newLlm("", "qwen3:8b");

        llm.chat(List.of(Map.of("role", "user", "content", "hi")), List.of());

        assertThat(authHeader.get()).isNull();
    }

    @Test
    @DisplayName("云端未配模型：报错指向 LLM_MODEL，不去探测 localhost")
    void t03_cloudRequiresExplicitModel() {
        OllamaAgentLlm llm = newLlm("sk-agent-test", "");
        ReflectionTestUtils.setField(llm, "baseUrl", "https://api.deepseek.com/v1/chat/completions");

        assertThatThrownBy(() -> llm.chat(List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_MODEL");
    }
}
