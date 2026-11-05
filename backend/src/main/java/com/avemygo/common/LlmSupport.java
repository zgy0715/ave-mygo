package com.avemygo.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 两个 LLM 客户端共用的端点逻辑（2026-11-05 抽出）。
 *
 * 背景：项目里有两个 LLM 客户端——
 *  · {@code agent/OllamaAgentLlm}（非流式，Agent 工具决策，v1/运营台）
 *  · {@code assistant/stream/OllamaStreamLlm}（流式，小灯 v2）
 *
 * 它们都要「解析模型名」和「按需带鉴权头」。这些逻辑原先各写一份，
 * 结果 2026-11-04 给流式端补云端鉴权时漏了非流式端——v1 兜底链路在云上直接 401。
 * 凡是"两个客户端都必须一致"的行为，一律放这里，避免再次分叉。
 */
public final class LlmSupport {

    private LlmSupport() {
    }

    /** 是否指向本机 Ollama（模型自动探测只对这个成立） */
    public static boolean isLocalOllama(String baseUrl) {
        if (baseUrl == null) return false;
        String b = baseUrl.toLowerCase();
        return b.contains("11434") || b.contains("localhost") || b.contains("127.0.0.1");
    }

    /**
     * 解析模型名：显式配置 &gt; 已探测缓存 &gt; 本机 Ollama 探测。
     * 云端（非本机地址）未配置模型时直接抛出清晰错误——云服务没有 /api/tags，盲目探测只会误导。
     *
     * @param configured 配置值 llm.model（可空）
     * @param cached     上次探测结果（可空）
     * @return 实际可用的模型名（调用方在返回值不等于 configured 时应回写缓存）
     */
    public static String resolveModel(String baseUrl, String configured, String cached,
                                      HttpClient http, ObjectMapper mapper) {
        if (configured != null && !configured.isBlank()) return configured;
        if (cached != null && !cached.isBlank()) return cached;
        if (!isLocalOllama(baseUrl)) {
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
                return first.asText();
            }
        } catch (Exception e) {
            // 探测失败统一走下面的报错，调用方不需要区分原因
        }
        throw new IllegalStateException("未配置 llm.model 且无法从 Ollama 探测模型");
    }

    /**
     * 鉴权头值：apiKey 非空返回 {@code "Bearer xxx"}，否则返回 null（本机 Ollama 不需要）。
     * 调用方用 null 判断是否添加请求头。
     */
    public static String bearer(String apiKey) {
        return apiKey == null || apiKey.isBlank() ? null : "Bearer " + apiKey;
    }
}
