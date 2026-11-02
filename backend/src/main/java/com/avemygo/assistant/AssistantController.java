package com.avemygo.assistant;

import com.avemygo.assistant.dto.AssistantDtos.ChatRequest;
import com.avemygo.assistant.dto.AssistantDtos.ChatResponse;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    @Qualifier("assistantStreamExecutor")
    private final ThreadPoolTaskExecutor streamExecutor;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(assistantService.chat(req));
    }

    /**
     * 流式问答（SSE）：
     *   data: {"delta":"…"} 逐段
     *   data: {"done":true,"intent":"…","persona":"…","personaName":"…"} 结束
     *
     * 说明：当前实现是"先拿到完整回复再按片段推送"（LLM 未提供 token 级流式）。
     * 对前端体验等价（可逐字渲染、可中断），后续换成真正的 token 流只需替换这里的分片循环。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(60_000L);
        streamExecutor.execute(() -> {
            try {
                ChatResponse res = assistantService.chat(req);
                String reply = res.reply() == null ? "" : res.reply();
                int chunk = 10;
                for (int i = 0; i < reply.length(); i += chunk) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("delta", reply.substring(i, Math.min(reply.length(), i + chunk)));
                    emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
                    Thread.sleep(18);            // 制造打字机节奏；客户端断开时 send 会抛异常并退出
                }
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("done", true);
                done.put("intent", res.intent());
                done.put("persona", res.persona());
                done.put("personaName", res.personaName());
                emitter.send(SseEmitter.event().data(done, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } catch (Exception e) {
                // 客户端提前断开（浏览器关闭/AbortController）会走到这里，属于正常情况
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
