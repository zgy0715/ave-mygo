package com.avemygo.assistant;

import com.avemygo.assistant.dto.AssistantDtos.ChatRequest;
import com.avemygo.assistant.dto.AssistantDtos.ChatResponse;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手接口（公开，userId 可空——游客可问）。
 * POST /api/v1/assistant/chat  问答入口：意图识别 → 库内数据注入 → LLM/规则引擎作答。
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(assistantService.chat(req));
    }
}
