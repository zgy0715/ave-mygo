package com.avemygo.assistant.stream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * Ollama 流式实现（OpenAI 兼容 /v1/chat/completions + stream=true）。
 *
 * 实测结论（2026-11-01，qwen3:8b）：
 *  - 首个 chunk 是 delta.reasoning（思考过程，约 0.5s 到达），正文 content 为空 → 用它触发「思考中」
 *  - reasoning 片段不展示给用户（8B 的思考是碎片化的，展示反而显得混乱），直接丢弃
 *  - tool_calls 是**整块**到达（不是增量拼接），随后一个 chunk 带 finish_reason=tool_calls
 *  - 结束时发送 data: [DONE]
 */
@Slf4j
@Component
public class OllamaStreamLlm implements StreamLlm {

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
    public Turn stream(List<Map<String, Object>> messages,
                       List<Map<String, Object>> tools,
                       Listener listener) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) body.put("tools", tools);
        body.put("stream", true);
        body.put("temperature", 0.3);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<java.io.InputStream> resp = http.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode());
        }

        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        boolean thinkingReported = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) continue;
                if ("[DONE]".equals(payload)) break;

                JsonNode delta;
                try {
                    delta = mapper.readTree(payload).path("choices").path(0).path("delta");
                } catch (Exception e) {
                    continue;   // 非法 chunk 跳过，不中断整轮
                }

                // 思考阶段：只上报一次「思考中」，内容丢弃
                if (!delta.path("reasoning").isMissingNode() && !thinkingReported) {
                    thinkingReported = true;
                    listener.onThinking();
                }

                JsonNode c = delta.path("content");
                if (c.isTextual() && !c.asText().isEmpty()) {
                    content.append(c.asText());
                    listener.onContent(c.asText());
                }

                for (JsonNode tc : delta.path("tool_calls")) {
                    String name = tc.path("function").path("name").asText();
                    if (name.isBlank()) continue;
                    String args = tc.path("function").path("arguments").isString()
                            ? tc.path("function").path("arguments").asText()
                            : tc.path("function").path("arguments").toString();
                    String id = tc.path("id").asText("call_" + toolCalls.size());
                    toolCalls.add(new ToolCall(id, name, args));
                    listener.onToolCall(id, name, args);
                }
            }
        }

        return new Turn(content.toString(), toolCalls);
    }

    /** 模型名：显式配置优先，否则探测 Ollama 已装模型并缓存 */
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
                return detectedModel;
            }
        } catch (Exception e) {
            log.debug("流式 LLM 模型探测失败: {}", e.getMessage());
        }
        throw new IllegalStateException("未配置 llm.model 且无法从 Ollama 探测模型");
    }
}
