package com.avemygo.assistant.stream;

import com.avemygo.assistant.stream.AssistantV2Dtos.ChatV2Request;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 小灯 v2 单一入口（docs/13 §3.1）：POST /api/v1/assistant/chat/v2
 *
 * 与 v1 的两点不同：
 *  1) 一个端点服务所有身份——游客/普通用户/管理员都走这里，前端不再判断模式
 *  2) 事件化 SSE：每帧 data 里带 type，前端按 type 渲染（delta/tool/confirm/done/error）
 *
 * v1 的 /assistant/chat、/assistant/chat/stream、/admin/agent/** 保留不动，作为回退。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantV2Controller {

    private final AssistantStreamService service;

    @Qualifier("assistantStreamExecutor")
    private final ThreadPoolTaskExecutor streamExecutor;

    @PostMapping(value = "/chat/v2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatV2(@Valid @RequestBody ChatV2Request req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Auth auth = currentAuth();

        streamExecutor.execute(() -> {
            try {
                service.chat(req.message(), req.conversationId(), req.persona(),
                        auth.userId(), auth.admin(), (type, data) -> send(emitter, type, data));
                emitter.complete();
            } catch (Exception e) {
                log.warn("v2 流式处理异常: {}", e.getMessage());
                try {
                    send(emitter, "error", Map.of("code", 50000, "message", "服务异常，请稍后再试"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.debug("v2 SSE 连接异常: {}", t.getMessage()));
        return emitter;
    }

    /** 发出一个事件帧：data = {type: "...", ...payload} */
    private void send(SseEmitter emitter, String type, Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        if (payload != null) body.putAll(payload);
        try {
            emitter.send(SseEmitter.event().data(body, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 客户端断开（关面板/切页）会走到这里，属于正常情况
            throw new IllegalStateException("sse send failed", e);
        }
    }

    /** 当前请求的身份：JwtAuthFilter 盖章的 principal 是 Long userId */
    private Auth currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return new Auth(null, false);
        boolean admin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        Long userId = auth.getPrincipal() instanceof Long id ? id : null;
        return new Auth(userId, admin);
    }

    private record Auth(Long userId, boolean admin) {
    }
}
