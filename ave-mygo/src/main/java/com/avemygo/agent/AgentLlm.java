package com.avemygo.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 的 LLM 抽象（docs/10 §3）。
 * 抽成接口的目的：测试里可以塞假 LLM 脚本，不需要真的起 Ollama。
 */
public interface AgentLlm {

    /** LLM 要求调用某个工具 */
    record ToolCall(String id, String name, String argumentsJson) {
    }

    /** 一轮 LLM 输出：要么是文本（结束），要么是若干工具调用 */
    record Turn(String content, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    /**
     * @param messages 完整消息列表（system/user/assistant/tool，OpenAI 协议结构）
     * @param tools    工具 Schema 清单
     */
    Turn chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws Exception;
}
