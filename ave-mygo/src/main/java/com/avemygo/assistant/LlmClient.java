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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM 客户端（OpenAI 兼容 /chat/completions 协议，JDK HttpClient 实现，零新增依赖）。
 * 默认对接智谱 GLM-4-Flash（免费档）：LLM_API_KEY 环境变量配置后自动启用；
 * 未配置 Key 时由 AssistantService 走规则引擎兜底，功能不残废。
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

    @Value("${llm.base-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String baseUrl;

    @Value("${llm.model:glm-4-flash}")
    private String model;

    /** Key 是否已配置（决定走 LLM 还是规则兜底） */
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 调用 chat/completions；失败抛异常由上层兜底。
     * messages: [{role, content}, ...]
     */
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "stream", false,
                    "temperature", 0.7);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
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
}
