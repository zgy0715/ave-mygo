package com.avemygo.assistant;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.avemygo.common.LlmSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM 客户端（OpenAI 兼容 /chat/completions 协议，JDK HttpClient 实现，零新增依赖）。
 * 默认对接本机 Ollama（http://localhost:11434）：启动 Ollama 后自动探测已装模型；
 * 模型名可用 LLM_MODEL 指定；也可切云端（如智谱）——LLM_BASE_URL/LLM_API_KEY 覆盖即可。
 * Ollama 未启动/调用失败时由 AssistantService 走规则引擎兜底，功能不残废。
 */
@Slf4j
@Component
public class LlmClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:http://localhost:11434/v1/chat/completions}")
    private String baseUrl;

    @Value("${llm.model:}")
    private String model;

    private volatile String detectedModel;   // Ollama 自动探测的模型名（懒加载缓存）

    /** 总开关（默认开）：Ollama 未启动时 chat() 会抛异常，上层自动兜底 */
    public boolean available() {
        return true;
    }

    /**
     * 调用 chat/completions；失败抛异常由上层兜底。
     * messages: [{role, content}, ...]
     */
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model", resolveModel(),
                    "messages", messages,
                    "stream", false,
                    "temperature", 0.7);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            // 空 key 就不发鉴权头（原来会发 "Bearer "，本机 Ollama 虽不介意但语义错误）
            String auth = LlmSupport.bearer(apiKey);
            if (auth != null) builder.header("Authorization", auth);
            HttpRequest request = builder.build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("LLM HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.warn("LLM 调用失败: {}", e.getMessage());
            throw new IllegalStateException("LLM 调用失败", e);
        }
    }

    /** 模型名解析：显式配置优先；Ollama 场景自动探测第一个已装模型并缓存 */
    /** 模型名：显式配置优先；本机 Ollama 未配置时探测并缓存（规则见 LlmSupport） */
    private String resolveModel() {
        String resolved = LlmSupport.resolveModel(baseUrl, model, detectedModel, http, mapper);
        if (resolved != null && !resolved.equals(model) && !resolved.equals(detectedModel)) {
            detectedModel = resolved;
            log.info("自动探测 Ollama 模型: {}", resolved);
        }
        return resolved;
    }
}
