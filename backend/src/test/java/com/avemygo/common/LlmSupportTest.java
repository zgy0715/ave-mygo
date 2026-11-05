package com.avemygo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * LlmSupport 单测（2026-11-05）。
 *
 * 这段逻辑被三个客户端共用（LlmClient / OllamaAgentLlm / OllamaStreamLlm），
 * 以前各写一份，改云端鉴权时漏改一处就出了 401——所以在这里一次性锁死规则。
 */
class LlmSupportTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("bearer：空/空白 Key 返回 null（不发鉴权头），否则加 Bearer 前缀")
    void t01_bearer() {
        assertThat(LlmSupport.bearer(null)).isNull();
        assertThat(LlmSupport.bearer("")).isNull();
        assertThat(LlmSupport.bearer("   ")).isNull();
        assertThat(LlmSupport.bearer("sk-abc")).isEqualTo("Bearer sk-abc");
    }

    @Test
    @DisplayName("isLocalOllama：11434 / localhost / 127.0.0.1 视为本机，云端地址不算")
    void t02_isLocalOllama() {
        assertThat(LlmSupport.isLocalOllama("http://localhost:11434/v1/chat/completions")).isTrue();
        assertThat(LlmSupport.isLocalOllama("http://127.0.0.1:11434/v1/chat/completions")).isTrue();
        assertThat(LlmSupport.isLocalOllama("https://api.deepseek.com/v1/chat/completions")).isFalse();
        assertThat(LlmSupport.isLocalOllama(null)).isFalse();
    }

    @Test
    @DisplayName("resolveModel：显式配置优先，且不回写缓存")
    void t03_configuredModelWins() {
        String resolved = LlmSupport.resolveModel(
                "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", null, http, mapper);
        assertThat(resolved).isEqualTo("deepseek-chat");
    }

    @Test
    @DisplayName("resolveModel：缓存命中时不再探测")
    void t04_cachedModelUsed() {
        String resolved = LlmSupport.resolveModel(
                "https://api.deepseek.com/v1/chat/completions", "", "cached-model", http, mapper);
        assertThat(resolved).isEqualTo("cached-model");
    }

    @Test
    @DisplayName("resolveModel：云端未配模型 → 报错指向 LLM_MODEL，不去探测 localhost")
    void t05_cloudWithoutModelFails() {
        assertThatThrownBy(() -> LlmSupport.resolveModel(
                "https://api.deepseek.com/v1/chat/completions", "", null, http, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_MODEL");
    }
}
