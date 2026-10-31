package com.avemygo.agent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/** Agent 运营层接口 DTO（docs/10 §8）。 */
public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentChatRequest(
            @NotBlank(message = "指令不能为空") String message,
            String conversationId
    ) {
    }

    /** 一次工具调用的简报，给前端展示"小灯做了什么" */
    public record AgentToolTrace(String name, String status, String summary) {
    }

    /**
     * type = reply（纯文本回复）| pending_confirmation（等待人工确认写操作）
     */
    public record AgentChatResponse(
            String type,
            String content,
            String token,
            String toolName,
            Map<String, Object> arguments,
            List<AgentToolTrace> toolCalls
    ) {
    }

    public record AgentConfirmRequest(
            @NotBlank(message = "token 不能为空") String token,
            Boolean approve
    ) {
    }

    public record AgentConfirmResponse(String status, String result) {
    }
}
