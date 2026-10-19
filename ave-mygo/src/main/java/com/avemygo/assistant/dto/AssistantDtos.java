package com.avemygo.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 助手请求/响应 DTO */
public class AssistantDtos {

    public record ChatRequest(
            @NotBlank @Size(max = 500) String message,
            @Size(max = 64) String sessionId,       // 前端 uuid；缺省则服务端按"匿名单轮"处理
            @Size(max = 20) String persona          // xiaodeng / anon / sakiko / mutsumi，缺省小灯
    ) {
    }

    public record ChatResponse(
            String reply, String intent,
            String persona, String personaName      // 回显角色 key 与显示名
    ) {
    }
}
