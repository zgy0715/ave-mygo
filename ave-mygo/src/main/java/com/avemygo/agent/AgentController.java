package com.avemygo.agent;

import com.avemygo.agent.dto.AgentDtos.AgentChatRequest;
import com.avemygo.agent.dto.AgentDtos.AgentChatResponse;
import com.avemygo.agent.dto.AgentDtos.AgentConfirmRequest;
import com.avemygo.agent.dto.AgentDtos.AgentConfirmResponse;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 运营层接口（docs/10 §8）。挂在 /admin/** 下，沿用 ADMIN 权限。
 * 普通访客问答仍走 /api/v1/assistant/chat，两者互不影响。
 */
@RestController
@RequestMapping("/api/v1/admin/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** 下达运营指令：LLM 自主调工具，写操作返回待确认卡片 */
    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest req) {
        return ApiResponse.ok(agentService.chat(req.message(), req.conversationId(), currentUserId()));
    }

    /** 确认/取消待执行的写操作 */
    @PostMapping("/confirm")
    public ApiResponse<AgentConfirmResponse> confirm(@Valid @RequestBody AgentConfirmRequest req) {
        boolean approve = Boolean.TRUE.equals(req.approve());
        return ApiResponse.ok(agentService.confirm(req.token(), approve, currentUserId()));
    }

    /** JwtAuthFilter 盖章的 principal 是 Long userId */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return 0L;
    }
}
