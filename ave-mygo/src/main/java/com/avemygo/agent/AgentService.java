package com.avemygo.agent;

import com.avemygo.agent.dto.AgentDtos.AgentChatResponse;
import com.avemygo.agent.dto.AgentDtos.AgentConfirmResponse;
import com.avemygo.agent.dto.AgentDtos.AgentToolTrace;
import com.avemygo.common.BizException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 循环（docs/10 §5 核心算法）。
 *
 * 一句话：把「用户的话 + 工具清单」交给 LLM，解析它要调什么工具，执行完把结果还给它，
 * 循环直到它给出文本结论；写操作就地暂停，等人工确认后再执行。
 *
 * 刻意不用 @Transactional 包住整个循环：LLM 是秒级外部 IO，包事务会长时间占用连接；
 * 审计每一行独立落库，天然可追溯。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    /** 防 LLM 死循环：单次指令最多 8 步工具往返 */
    private static final int MAX_STEPS = 8;
    /** 写操作的确认有效期 */
    private static final int PENDING_TTL_MINUTES = 10;
    /** 结果落库/回填的截断长度 */
    private static final int RESULT_LIMIT = 2000;
    /** 多轮上下文：带入最近 N 条人机对话（工具输出不入上下文，避免淹没指令） */
    private static final int HISTORY_LIMIT = 8;

    private static final String SYSTEM_PROMPT = """
            你是「小灯」，本管理端的运营专员，可以调用工具读写站内数据（BanG Dream! Live 粉丝站）。
            规则：
            1. 只处理站内数据与运营指令，站外话题礼貌拒绝并引导回运营任务。
            2. 操作前先用只读工具确认对象与现状（拿到真实 id），禁止凭猜测填 id。
            3. 写操作会被系统拦截转人工确认，你只需提出调用，不要解释这个机制。
            4. 工具返回错误时，按错误信息调整参数再试一次；仍失败就如实说明原因。
            5. 用户说「所有 / 全部」时，不要自行附加状态过滤条件（如 status=PUBLISHED），
               要列出全部记录再说明各自状态。
            6. 回答用中文，简洁（150 字以内），直接给结论。
            """;

    private final AgentLlm llm;
    private final AgentToolRegistry registry;
    private final AgentToolCallRepository auditRepository;
    private final AgentMessageRepository messageRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    // ---------------- 对话主循环 ----------------

    public AgentChatResponse chat(String message, String conversationId, Long userId) {
        String conv = (conversationId == null || conversationId.isBlank())
                ? "c-" + UUID.randomUUID().toString().substring(0, 8)
                : conversationId.trim();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        // 多轮上下文：把本会话最近几条人机对话放进 messages，让"刚才那场""为什么没删掉"有据可依
        appendHistory(conv, messages);
        messages.add(Map.of("role", "user", "content", message));
        persist(conv, userId, AgentMessage.USER, message);

        List<AgentToolTrace> traces = new ArrayList<>();

        for (int step = 0; step < MAX_STEPS; step++) {
            AgentLlm.Turn turn = callLlm(messages);

            if (!turn.hasToolCalls()) {
                String content = turn.content() == null || turn.content().isBlank()
                        ? "（没有更多可执行的动作了）" : turn.content();
                persist(conv, userId, AgentMessage.ASSISTANT, content);
                return new AgentChatResponse("reply", content, null, null, null, traces);
            }

            // 协议要求：把 assistant 的 tool_calls 原样回填进消息流
            messages.add(assistantMessage(turn));

            for (AgentLlm.ToolCall call : turn.toolCalls()) {
                AgentTool tool = registry.get(call.name());

                if (tool == null) {
                    String err = "未知工具「" + call.name() + "」，请从提供的工具清单中选择。";
                    record(conv, userId, call.name(), "UNKNOWN", "{}", err, AgentToolCall.FAILED, null);
                    traces.add(new AgentToolTrace(call.name(), AgentToolCall.FAILED, "未知工具"));
                    messages.add(toolMessage(call.id(), err));
                    continue;
                }

                Map<String, Object> args = parseArgs(call.argumentsJson());

                if (tool.isWrite()) {
                    // 写操作：落 PENDING 审计 + 返回确认卡片，循环在此暂停
                    String token = UUID.randomUUID().toString();
                    String argsJson = toJson(args);
                    record(conv, userId, tool.name(), tool.risk().name(), argsJson,
                            null, AgentToolCall.PENDING, token);
                    traces.add(new AgentToolTrace(tool.name(), "PENDING", summarize(tool, args)));
                    String plan = "准备执行：" + summarize(tool, args) + "。请确认后我才会动手。";
                    persist(conv, userId, AgentMessage.ASSISTANT, plan);
                    return new AgentChatResponse("pending_confirmation", plan,
                            token, tool.name(), args, traces);
                }

                // 只读操作：直接执行，异常也要变成文本回填给 LLM（让它自己纠错）
                String result;
                String status;
                try {
                    result = tool.handler().execute(args);
                    status = AgentToolCall.EXECUTED;
                } catch (Exception e) {
                    result = "执行失败：" + rootMessage(e);
                    status = AgentToolCall.FAILED;
                }
                record(conv, userId, tool.name(), tool.risk().name(), toJson(args),
                        result, status, null);
                traces.add(new AgentToolTrace(tool.name(), status, truncate(result)));
                messages.add(toolMessage(call.id(), truncate(result)));
            }
        }

        String limitMsg = "任务步数超过上限（" + MAX_STEPS + " 步），请把指令拆成更小的一步再试。";
        persist(conv, userId, AgentMessage.ASSISTANT, limitMsg);
        return new AgentChatResponse("reply", limitMsg, null, null, null, traces);
    }

    // ---------------- 确认门控 ----------------

    public AgentConfirmResponse confirm(String token, boolean approve, Long userId) {
        AgentToolCall row = auditRepository.findByToken(token)
                .orElseThrow(() -> new BizException(40004, "确认请求不存在"));

        if (!AgentToolCall.PENDING.equals(row.getStatus())) {
            throw new BizException(40902, "该操作已处理或已失效，请重新发起指令");
        }
        if (!row.getUserId().equals(userId)) {
            throw new BizException(40902, "无权确认他人发起的操作");
        }
        if (row.getCreatedAt() != null
                && row.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(PENDING_TTL_MINUTES))) {
            row.setStatus(AgentToolCall.EXPIRED);
            auditRepository.save(row);
            throw new BizException(40902, "确认已过期（超过 " + PENDING_TTL_MINUTES + " 分钟），请重新发起");
        }

        if (!approve) {
            row.setStatus(AgentToolCall.REJECTED);
            auditRepository.save(row);
            persist(row.getConversationId(), row.getUserId(), AgentMessage.ASSISTANT,
                    "（工具执行结果）已取消，未修改任何数据");
            return new AgentConfirmResponse(AgentToolCall.REJECTED, "已取消，未修改任何数据");
        }

        AgentTool tool = registry.get(row.getToolName());
        if (tool == null) {
            row.setStatus(AgentToolCall.FAILED);
            row.setResult("工具已不存在：" + row.getToolName());
            auditRepository.save(row);
            return new AgentConfirmResponse(AgentToolCall.FAILED, "工具已不存在，未执行任何修改");
        }

        try {
            String result = tool.handler().execute(parseArgs(row.getArgumentsJson()));
            row.setStatus(AgentToolCall.EXECUTED);
            row.setResult(truncate(result));
            auditRepository.save(row);
            // 补记结果进会话：之后追问"刚才删掉了吗"能答上
            persist(row.getConversationId(), row.getUserId(), AgentMessage.ASSISTANT,
                    "（工具执行结果）" + result);
            return new AgentConfirmResponse(AgentToolCall.EXECUTED, result);
        } catch (Exception e) {
            String msg = "执行失败：" + rootMessage(e);
            row.setStatus(AgentToolCall.FAILED);
            row.setResult(truncate(msg));
            auditRepository.save(row);
            persist(row.getConversationId(), row.getUserId(), AgentMessage.ASSISTANT,
                    "（工具执行结果）" + msg);
            return new AgentConfirmResponse(AgentToolCall.FAILED, msg);
        }
    }

    // ---------------- 内部工具 ----------------

    private AgentLlm.Turn callLlm(List<Map<String, Object>> messages) {
        try {
            return llm.chat(messages, registry.schemas());
        } catch (Exception e) {
            log.warn("Agent LLM 调用失败: {}", e.getMessage());
            throw new BizException(50301, "Agent 需要 LLM 才能工作（请确认 Ollama 已启动并加载模型）");
        }
    }

    private Map<String, Object> assistantMessage(AgentLlm.Turn turn) {
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        for (AgentLlm.ToolCall c : turn.toolCalls()) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", c.name());
            fn.put("arguments", c.argumentsJson());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.id());
            item.put("type", "function");
            item.put("function", fn);
            toolCalls.add(item);
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", turn.content() == null ? "" : turn.content());
        msg.put("tool_calls", toolCalls);
        return msg;
    }

    private Map<String, Object> toolMessage(String callId, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", callId);
        msg.put("content", content);
        return msg;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object parsed = mapper.readValue(json, Object.class);
            return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (Exception e) {
            log.debug("工具参数不是合法 JSON，按空参数处理: {}", json);
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> args) {
        try {
            return mapper.writeValueAsString(args);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void record(String conv, Long userId, String toolName, String risk,
                        String argsJson, String result, String status, String token) {
        try {
            AgentToolCall row = new AgentToolCall();
            row.setConversationId(conv);
            row.setUserId(userId == null ? 0L : userId);
            row.setToolName(toolName);
            row.setRisk(risk);
            row.setArgumentsJson(argsJson == null ? "{}" : argsJson);
            row.setResult(result == null ? null : truncate(result));
            row.setStatus(status);
            row.setToken(token);
            auditRepository.save(row);
        } catch (Exception e) {
            // 审计失败不能拖垮主流程，但要留痕
            log.error("Agent 审计写入失败 tool={} status={}: {}", toolName, status, e.getMessage());
        }
    }

    /** 多轮上下文：把本会话最近 N 条人机对话追加进消息列表（时间正序） */
    private void appendHistory(String conv, List<Map<String, Object>> messages) {
        try {
            List<AgentMessage> recent = messageRepository.findByConversationIdOrderByIdDesc(
                    conv, org.springframework.data.domain.PageRequest.of(0, HISTORY_LIMIT));
            List<AgentMessage> chronological = new ArrayList<>(recent);
            java.util.Collections.reverse(chronological);
            for (AgentMessage m : chronological) {
                String role = AgentMessage.USER.equals(m.getRole()) ? "user" : "assistant";
                messages.add(Map.of("role", role, "content", m.getContent()));
            }
        } catch (Exception e) {
            // 历史读失败不影响本次指令
            log.warn("Agent 历史载入失败: {}", e.getMessage());
        }
    }

    private void persist(String conv, Long userId, String role, String content) {
        if (conv == null || content == null || content.isBlank()) return;
        try {
            AgentMessage m = new AgentMessage();
            m.setConversationId(conv);
            m.setUserId(userId == null ? 0L : userId);
            m.setRole(role);
            m.setContent(truncate(content));
            messageRepository.save(m);
        } catch (Exception e) {
            // 落库失败不能拖垮对话主流程
            log.warn("Agent 会话消息写入失败: {}", e.getMessage());
        }
    }

    /** 给用户看的动作摘要，也是确认卡片正文 */
    private String summarize(AgentTool tool, Map<String, Object> args) {
        return switch (tool.name()) {
            case "update_song_mv" -> "修改歌曲 id=%s 的 MV 地址为 %s".formatted(args.get("songId"), args.get("mvUrl"));
            case "set_ticket_status" -> "%s票档 id=%s".formatted(
                    String.valueOf(args.get("status")).equals("1") ? "恢复上架" : "下架", args.get("ticketTypeId"));
            case "delete_session" -> "删除场次 id=%s（票档与曲目单一并移除）".formatted(args.get("sessionId"));
            case "delete_event" -> "删除演出 id=%s".formatted(args.get("eventId"));
            default -> tool.description();
        };
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= RESULT_LIMIT ? s : s.substring(0, RESULT_LIMIT) + "…（已截断）";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
