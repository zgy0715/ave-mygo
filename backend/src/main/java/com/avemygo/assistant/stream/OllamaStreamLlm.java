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
 * 流式 LLM 实现（OpenAI 兼容 /v1/chat/completions + stream=true）。
 *
 * 同一份代码兼容两类后端（2026-11-03，docs/19 P0-2）：
 *  - 本地 Ollama：不配 LLM_API_KEY → 不发 Authorization，模型可自动探测；
 *  - 云端（DeepSeek / 百炼 / SiliconFlow）：配 LLM_API_KEY → 发 Bearer 头，
 *    且必须显式配置 LLM_MODEL（云端无法探测），tool_calls 按 index 增量拼接。
 *
 * 实测结论（2026-11-01，qwen3:8b）：
 *  - 首个 chunk 是 delta.reasoning（思考过程，约 0.5s 到达），正文 content 为空 → 用它触发「思考中」
 *  - reasoning 片段不展示给用户（8B 的思考是碎片化的，展示反而显得混乱），直接丢弃
 *  - Ollama 的 tool_calls 是整块到达；云端标准协议是按 index 增量分片——统一交给
 *    {@link OpenAiStreamAccumulator} 按 index 累加，两种格式都能拼出完整参数
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

    /** 云 API Key（DeepSeek / 百炼 / SiliconFlow 等）；本地 Ollama 留空则不发鉴权头 */
    @Value("${llm.api-key:}")
    private String apiKey;

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

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);   // 云 API 必需；Ollama 不需要
        }
        HttpRequest request = builder.build();

        HttpResponse<java.io.InputStream> resp = http.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode());
        }

        OpenAiStreamAccumulator acc = new OpenAiStreamAccumulator();
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

                OpenAiStreamAccumulator.Delta d = acc.accept(delta);
                if (d.thinkingStarted() && !thinkingReported) {
                    thinkingReported = true;
                    listener.onThinking();      // 只上报一次，内容不展示
                }
                if (d.contentPiece() != null) {
                    listener.onContent(d.contentPiece());
                }
            }
        }

        // tool_calls 在流结束后统一给出完整参数（增量分片已按 index 拼好）
        List<ToolCall> toolCalls = acc.toolCalls();
        for (ToolCall call : toolCalls) {
            listener.onToolCall(call.id(), call.name(), call.argumentsJson());
        }
        return new Turn(acc.content(), toolCalls);
    }

    /**
     * 模型名：显式配置优先；未配置时只对本地 Ollama 做探测（云端必须显式给模型名，
     * 因为云服务没有 /api/tags 这类探测端点，盲目探测只会浪费时间并报出误导性错误）。
     */
    private String resolveModel() {
        if (model != null && !model.isBlank()) return model;
        if (detectedModel != null) return detectedModel;
        if (!isLocalOllama()) {
            throw new IllegalStateException(
                    "云端 LLM 必须显式配置 LLM_MODEL（如 deepseek-chat / qwen-plus / Qwen/Qwen2.5-7B-Instruct）");
        }
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

    /** 是否指向本机 Ollama（探测模型只对这个成立） */
    private boolean isLocalOllama() {
        return baseUrl.contains("11434") || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");
    }
}
