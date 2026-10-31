package com.avemygo.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具定义（docs/10 §4.2）。
 * 一个工具 = 给 LLM 的说明书（name/description/parameters）+ 真正执行它的代码（handler）。
 * 风险等级决定是否要人工确认：READ 直接执行，WRITE 必须过门控。
 */
public record AgentTool(
        String name,
        String description,
        Risk risk,
        Map<String, Object> parameters,
        ToolHandler handler
) {

    public enum Risk { READ, WRITE }

    /** 执行体：入参是 LLM 给的原始参数，返回给 LLM 看的文本结果（异常由调用方转成错误文本回填） */
    @FunctionalInterface
    public interface ToolHandler {
        String execute(Map<String, Object> args) throws Exception;
    }

    public boolean isWrite() {
        return risk == Risk.WRITE;
    }

    /** 转成 OpenAI / Ollama tools 协议格式，直接塞进请求体 */
    public Map<String, Object> toOpenAiSchema() {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", fn);
        return wrapper;
    }
}
