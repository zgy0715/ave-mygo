package com.avemygo.assistant.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;

/**
 * OpenAI 兼容流的增量累加器（V56，docs/19 P0-2）。
 *
 * 两种后端的流式行为差异（实测）：
 *  - Ollama（qwen3:8b）：tool_calls <b>整块</b>到达，一个 chunk 就带完整 arguments；
 *  - 云端 OpenAI 兼容（DeepSeek / 百炼 / SiliconFlow）：tool_calls <b>按 index 增量分片</b>，
 *    arguments 被切成多段文本，必须按 index 累加，否则只能拿到半截 JSON，工具调用全废。
 *
 * 统一策略：不管整块还是分片，都按 index 累加——整块只是「只拼一次」的特例。
 * 因此 Ollama 与云端共用这一份解析，切换后端不用改代码。
 */
class OpenAiStreamAccumulator {

    /** 累加中的一条工具调用（分片到齐前是不完整的） */
    private static final class Pending {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    private final StringBuilder content = new StringBuilder();
    /** TreeMap：toolCalls() 按 index 升序返回（分片可能乱序到达） */
    private final Map<Integer, Pending> pending = new TreeMap<>();
    private boolean thinking = false;

    /** 一条 delta 的解析结果 */
    record Delta(boolean thinkingStarted, String contentPiece) {}

    /**
     * 接收一个 choices[0].delta 节点，累积并返回本片摘要。
     *
     * @return thinkingStarted=是否为本轮第一个思考分片（用于只上报一次「思考中」）；
     *         contentPiece=本次正文增量（无则 null）
     */
    Delta accept(JsonNode delta) {
        boolean thinkingNow = false;
        if (!delta.path("reasoning").isMissingNode() && !thinking) {
            thinking = true;
            thinkingNow = true;
        }

        String contentPiece = null;
        JsonNode c = delta.path("content");
        if (c.isTextual() && !c.asText().isEmpty()) {
            content.append(c.asText());
            contentPiece = c.asText();
        }

        for (JsonNode tc : delta.path("tool_calls")) {
            // 无 index 时（Ollama 整块）视为第 0 条；多条整块按出现顺序排
            int index = tc.path("index").asInt(pending.size());
            Pending p = pending.computeIfAbsent(index, k -> new Pending());
            JsonNode fn = tc.path("function");
            String name = fn.path("name").asText("");
            if (!name.isBlank()) {
                p.name = name;               // 分片时通常只有首片带 name
            }
            String id = tc.path("id").asText("");
            if (!id.isBlank()) {
                p.id = id;
            }
            JsonNode args = fn.path("arguments");
            if (args.isTextual()) {
                p.arguments.append(args.asText());   // 标准协议：字符串分段传
            } else if (!args.isMissingNode()) {
                p.arguments.append(args.toString()); // 兜底：某些实现直接给对象
            }
        }
        return new Delta(thinkingNow, contentPiece);
    }

    /** 本轮正文全文 */
    String content() {
        return content.toString();
    }

    /** 流结束后取完整的工具调用列表（按 index 顺序，缺 id 的补默认值） */
    List<StreamLlm.ToolCall> toolCalls() {
        List<StreamLlm.ToolCall> out = new ArrayList<>();
        int i = 0;
        for (Pending p : pending.values()) {
            out.add(new StreamLlm.ToolCall(
                    p.id == null || p.id.isBlank() ? "call_" + i : p.id,
                    p.name == null ? "" : p.name,
                    p.arguments.toString()));
            i++;
        }
        return out;
    }
}
