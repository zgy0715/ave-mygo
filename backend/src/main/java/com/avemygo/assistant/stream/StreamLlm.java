package com.avemygo.assistant.stream;

import java.util.List;
import java.util.Map;

/**
 * 流式 LLM 抽象（小灯 v2，docs/13）。
 * 与 AgentLlm（非流式）并存：v1 运营接口仍用 AgentLlm，v2 统一入口用本接口。
 *
 * 设计要点：回调式而不是返回整体——只有回调才能把首字尽快推给前端。
 */
public interface StreamLlm {

    /** 流式过程中的回调（在调用线程上同步触发） */
    interface Listener {
        /** 模型开始思考（用于前端立即显示「思考中」，避免干等） */
        void onThinking();

        /** 正文增量（真正的打字机来源） */
        void onContent(String delta);

        /** 模型要求调用工具（流结束后每条回调一次，参数已按 index 拼成完整 JSON） */
        void onToolCall(String id, String name, String argumentsJson);
    }

    /**
     * 发起一轮流式请求，阻塞直到本轮结束（内容输出完或要求调工具）。
     *
     * @return 本轮产出的正文全文 + 工具调用列表（调用方据此决定是否进入下一轮）
     */
    Turn stream(List<Map<String, Object>> messages,
                List<Map<String, Object>> tools,
                Listener listener) throws Exception;

    /** 一轮的结果 */
    record Turn(String content, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    record ToolCall(String id, String name, String argumentsJson) {
    }
}
