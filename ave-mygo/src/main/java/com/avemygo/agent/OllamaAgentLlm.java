package com.avemygo.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ollama 实现（OpenAI 兼容 /v1/chat/completions + tools 协议）。
 * 与小灯的 LlmClient 区别：这里必须支持 function calling（tools/tool_calls），
 * 且不做规则兜底——Agent 无法用规则模拟「自主决策调工具」。
 */
@Slf4j
@Component
public class OllamaAgentLlm implements AgentLlm {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${llm.base-url:http://localhost:11434/v1/chat/completions}")
    private String baseUrl;

    @Value("${llm.model:}")
    private String model;

    private volatile String detectedModel;

    @Override
    public Turn chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel());
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("stream", false);
        body.put("temperature", 0.2);   // Agent 要稳定，不要发挥

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode());
        }

        JsonNode message = mapper.readTree(resp.body()).path("choices").path(0).path("message");
        String content = message.path("content").isMissingNode() ? "" : message.path("content").asText("");
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode tc : message.path("tool_calls")) {
            String args = tc.path("function").path("arguments").isString()
                    ? tc.path("function").path("arguments").asText()
                    : tc.path("function").path("arguments").toString();
            calls.add(new ToolCall(
                    tc.path("id").asText("call_" + calls.size()),
                    tc.path("function").path("name").asText(),
                    args));
        }
        return new Turn(content, calls);
    }

    /** 模型名：显式配置优先；否则向 Ollama 探测第一个已装模型并缓存 */
    private String resolveModel() {
        if (model != null && !model.isBlank()) return model;
        if (detectedModel != null) return detectedModel;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode first = mapper.readTree(resp.body()).path("models").path(0).path("name");
            if (!first.isMissingNode() && !first.asText().isBlank()) {
                detectedModel = first.asText();
                log.info("Agent 自动探测 Ollama 模型: {}", detectedModel);
                return detectedModel;
            }
        } catch (Exception e) {
            log.debug("Agent 模型探测失败: {}", e.getMessage());
        }
        throw new IllegalStateException("未配置 llm.model 且无法从 Ollama 探测模型");
    }
}
