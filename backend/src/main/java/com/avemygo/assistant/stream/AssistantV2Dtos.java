package com.avemygo.assistant.stream;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 小灯 v2 请求（单一入口，docs/13 §3.1）。 */
public final class AssistantV2Dtos {

    private AssistantV2Dtos() {
    }

    public record ChatV2Request(
            @NotBlank(message = "消息不能为空")
            @Size(max = 500, message = "单次消息不要超过 500 字")
            String message,
            String conversationId,
            String persona
    ) {
    }
}
