package com.avemygo.assistant.stream;

import com.avemygo.agent.AgentMessage;
import com.avemygo.agent.AgentMessageRepository;
import com.avemygo.agent.AgentTool;
import com.avemygo.agent.AgentToolCall;
import com.avemygo.agent.AgentToolCallRepository;
import com.avemygo.agent.AgentToolRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 小灯 v2 流式编排（docs/13）。
 *
 * 与 v1 的区别：
 *  1) 单一入口：不再区分「问答/运营」两个接口、两个模式，前端只连一个流
 *  2) 真流式：模型边生成边推 delta（v1 是"生成完再切片"，首字=全生成时间）
 *  3) 身份闸门：非管理员拿到的工具清单里根本没有写工具
 *  4) 质量处方：能力自述、禁空话反问、先查后改、批量拒绝、同错两次即停、确认卡片带对象名
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantStreamService {

    /** 一轮对话最多几轮工具往返 */
    private static final int MAX_ROUNDS = 4;
    /** 带入上下文的历史条数 */
    private static final int HISTORY_LIMIT = 10;
    /** 同一工具连续失败多少次就停下来问用户 */
    private static final int MAX_SAME_TOOL_FAILS = 2;

    /** 纯寒暄：不挂工具，直接秒回（省掉一次工具决策的 3~6 秒） */
    private static final Pattern GREETING = Pattern.compile(
            "^(你好|您好|hi|hello|哈喽|嗨|在吗|在不在|早上好|晚上好|早|谢谢|多谢|感谢|再见|拜拜)[!！。.~～\\s]*$",
            Pattern.CASE_INSENSITIVE);

    private static final String SYSTEM_PROMPT = """
            你是「小灯」，BanG Dream! 粉丝站「BanG Dream! Live」的站内智能助手。
            【你能做的事】
            · 查站内数据：歌曲（搜索/MV/标签）、乐队、成员（含 CV）、演出、场次、票档与余票
            · 若调用方给了写工具：改 MV、改标签、上下架歌曲、票档上下架、改演出标题/场馆、改场次时间、删场次/演出
            【回答规则】
            1. 需要站内数据时必须先调用工具，不要凭印象编；工具没返回的就说不确定。
            2. 涉及具体对象的修改，必须先查到真实 id 并说出对象名字（如《迷星叫》），再提出修改。
            3. 参数不够就直接问最关键的那一个澄清问题，绝不猜 id、不猜价格、不猜日期。
            4. 用户要求批量删除或批量修改时，拒绝并请他一次只处理一个对象。
            5. 信息已经足够时直接给结论，**不要**用「是否需要…？」「还需要我做什么吗？」结尾。
            6. 用中文，亲切但不啰嗦；不要输出思考过程，不要解释你在调用工具。
            7. 不要使用 Markdown 语法（**加粗**、# 标题、``` 代码块都不行）——前端按纯文本渲染，
               需要列举时用「1. 」「· 」开头即可。/no_think
            """;

    private final StreamLlm streamLlm;
    private final AgentToolRegistry registry;
    private final AgentToolCallRepository auditRepository;
    private final AgentMessageRepository messageRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 事件出口：由 Controller 适配成 SSE */
    public interface Sink {
        void emit(String type, Map<String, Object> data);
    }

    /**
     * 处理一次对话（阻塞到本轮结束；由 Controller 放到异步线程池里执行）。
     *
     * @param admin 是否管理员——决定工具清单里有没有写工具
     */
    public void chat(String message, String conversationId, String persona, Long userId,
                     boolean admin, Sink sink) {
        long startedAt = System.currentTimeMillis();
        String conv = (conversationId == null || conversationId.isBlank())
                ? "c-" + UUID.randomUUID().toString().substring(0, 8) : conversationId.trim();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        appendHistory(conv, messages);
        messages.add(Map.of("role", "user", "content", message));
        persist(conv, userId, AgentMessage.USER, message);

        // 身份闸门 + 寒暄快速通道（结构上：写工具仅管理员可见）
        List<Map<String, Object>> tools = GREETING.matcher(message.trim()).matches()
                ? List.of()
                : registry.schemas(admin);

        int usedTools = 0;
        Map<String, Integer> toolFails = new HashMap<>();
        // 流式出口的 markdown 清洗器：模型不听话时由代码兜底（前端纯文本渲染，星号会裸露）
        MarkdownStripper stripper = new MarkdownStripper();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            StreamLlm.Turn turn;
            try {
                turn = streamLlm.stream(messages, tools, new StreamLlm.Listener() {
                    @Override
                    public void onThinking() {
                        sink.emit("status", Map.of("phase", "thinking"));
                    }

                    @Override
                    public void onContent(String delta) {
                        String clean = stripper.push(delta);
                        if (!clean.isEmpty()) sink.emit("delta", Map.of("text", clean));
                    }

                    @Override
                    public void onToolCall(String id, String name, String args) {
                        // 工具调用在正文之后才处理，这里不推事件（避免"正在调用"闪一下）
                    }
                });
            } catch (Exception e) {
                log.warn("v2 流式 LLM 调用失败: {}", e.getMessage());
                sink.emit("error", Map.of("code", 50301,
                        "message", "小灯暂时离线（LLM 服务未就绪），稍后再试"));
                return;
            }

            // 无工具调用 = 本轮就是最终回答
            if (!turn.hasToolCalls()) {
                String content = MarkdownStripper.strip(turn.content() == null ? "" : turn.content());
                persist(conv, userId, AgentMessage.ASSISTANT, content);
                sink.emit("done", Map.of("usedTools", usedTools, "conversationId", conv,
                        "elapsedMs", System.currentTimeMillis() - startedAt));
                return;
            }

            // 有工具调用：把 assistant 的 tool_calls 回填，再逐个处理
            messages.add(assistantMessage(turn));

            for (StreamLlm.ToolCall call : turn.toolCalls()) {
                AgentTool tool = registry.get(call.name());
                Map<String, Object> args = parseArgs(call.argumentsJson());

                if (tool == null) {
                    String err = "未知工具「" + call.name() + "」，请从可用工具中选择。";
                    audit(conv, userId, call.name(), "UNKNOWN", toJson(args), err, AgentToolCall.FAILED, null);
                    messages.add(toolMessage(call.id(), err));
                    sink.emit("tool", Map.of("name", call.name(), "status", "FAIL", "summary", "未知工具"));
                    continue;
                }

                // 双闸门：非管理员即便模型硬编出写工具，也不执行
                if (tool.isWrite() && !admin) {
                    String err = "当前身份没有修改权限。";
                    messages.add(toolMessage(call.id(), err));
                    sink.emit("tool", Map.of("name", tool.name(), "status", "FAIL", "summary", "无权限"));
                    continue;
                }

                // 写操作：不执行，落 PENDING 并把确认卡片推给前端（一次指令只允许一张）
                if (tool.isWrite()) {
                    String token = UUID.randomUUID().toString();
                    String argsJson = toJson(args);
                    audit(conv, userId, tool.name(), tool.risk().name(), argsJson,
                            null, AgentToolCall.PENDING, token);
                    String label = registry.describe(tool.name(), args);
                    String preview = (label == null ? "对象 id 见参数" : label)
                            + " → " + readableArgs(tool.name(), args);
                    persist(conv, userId, AgentMessage.ASSISTANT, "准备执行：" + preview);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("token", token);
                    payload.put("toolName", tool.name());
                    payload.put("title", label == null ? tool.name() : label);
                    payload.put("arguments", args);
                    payload.put("preview", preview);
                    sink.emit("confirm", payload);
                    sink.emit("done", Map.of("usedTools", usedTools + 1, "conversationId", conv,
                            "pending", true, "elapsedMs", System.currentTimeMillis() - startedAt));
                    return;
                }

                // 只读工具：执行并回填（失败也要把原因给模型，让它自己决定下一步）
                usedTools++;
                sink.emit("tool", Map.of("name", tool.name(), "status", "RUNNING",
                        "summary", runningSummary(tool.name())));
                String result;
                String status;
                try {
                    result = tool.handler().execute(args);
                    status = AgentToolCall.EXECUTED;
                } catch (Exception e) {
                    result = "执行失败：" + rootMessage(e);
                    status = AgentToolCall.FAILED;
                    int fails = toolFails.merge(tool.name(), 1, Integer::sum);
                    if (fails >= MAX_SAME_TOOL_FAILS) {
                        // 处方 4：同一个工具连错两次就停，别让它无限重试烧时间
                        audit(conv, userId, tool.name(), tool.risk().name(), toJson(args),
                                result, status, null);
                        sink.emit("tool", Map.of("name", tool.name(), "status", "FAIL",
                                "summary", truncate(result, 80)));
                        String giveUp = "这个操作连续失败了两次（" + rootError(e)
                                + "）。我先停下——要不要换个说法，或者告诉我更具体的信息？";
                        persist(conv, userId, AgentMessage.ASSISTANT, giveUp);
                        sink.emit("delta", Map.of("text", giveUp));
                        sink.emit("done", Map.of("usedTools", usedTools, "conversationId", conv,
                                "elapsedMs", System.currentTimeMillis() - startedAt));
                        return;
                    }
                }
                audit(conv, userId, tool.name(), tool.risk().name(), toJson(args),
                        result, status, null);
                sink.emit("tool", Map.of("name", tool.name(), "status",
                        AgentToolCall.EXECUTED.equals(status) ? "OK" : "FAIL",
                        "summary", truncate(result.replace("\n", " "), 80)));
                messages.add(toolMessage(call.id(), truncate(result, 2000)));
            }
        }

        String limit = "这次要查的东西有点多，我绕了几圈还没理清。你把要求说得再具体一点？";
        persist(conv, userId, AgentMessage.ASSISTANT, limit);
        sink.emit("delta", Map.of("text", limit));
        sink.emit("done", Map.of("usedTools", usedTools, "conversationId", conv,
                "elapsedMs", System.currentTimeMillis() - startedAt));
    }

    // ---------------- 内部 ----------------

    private void appendHistory(String conv, List<Map<String, Object>> messages) {
        try {
            List<AgentMessage> recent = messageRepository.findByConversationIdOrderByIdDesc(
                    conv, PageRequest.of(0, HISTORY_LIMIT));
            List<AgentMessage> chronological = new ArrayList<>(recent);
            java.util.Collections.reverse(chronological);
            for (AgentMessage m : chronological) {
                messages.add(Map.of(
                        "role", AgentMessage.USER.equals(m.getRole()) ? "user" : "assistant",
                        "content", MarkdownStripper.strip(m.getContent())));
            }
        } catch (Exception e) {
            log.warn("v2 历史载入失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> assistantMessage(StreamLlm.Turn turn) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (StreamLlm.ToolCall c : turn.toolCalls()) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", c.name());
            fn.put("arguments", c.argumentsJson());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.id());
            item.put("type", "function");
            item.put("function", fn);
            calls.add(item);
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", turn.content() == null ? "" : turn.content());
        msg.put("tool_calls", calls);
        return msg;
    }

    private Map<String, Object> toolMessage(String callId, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", callId);
        msg.put("content", content);
        return msg;
    }

    /** 工具运行中给用户看的短句（不要暴露技术细节） */
    private String runningSummary(String toolName) {
        return switch (toolName) {
            case "search_songs", "get_song" -> "正在查歌曲…";
            case "list_events", "get_event" -> "正在查演出…";
            case "list_sessions" -> "正在查场次与票档…";
            case "list_members" -> "正在查成员…";
            case "list_bands" -> "正在查乐队…";
            default -> "正在查询…";
        };
    }

    /** 写操作参数的简写（确认卡片第二行用） */
    private String readableArgs(String toolName, Map<String, Object> args) {
        StringBuilder sb = new StringBuilder();
        args.forEach((k, v) -> {
            if (sb.length() > 0) sb.append("，");
            String val = String.valueOf(v);
            sb.append(k).append("=").append(val.length() > 60 ? val.substring(0, 60) + "…" : val);
        });
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object parsed = mapper.readValue(json, Object.class);
            return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void audit(String conv, Long userId, String tool, String risk, String argsJson,
                       String result, String status, String token) {
        try {
            AgentToolCall row = new AgentToolCall();
            row.setConversationId(conv);
            row.setUserId(userId == null ? 0L : userId);
            row.setToolName(tool);
            row.setRisk(risk);
            row.setArgumentsJson(argsJson == null ? "{}" : argsJson);
            row.setResult(result == null ? null : truncate(result, 2000));
            row.setStatus(status);
            row.setToken(token);
            auditRepository.save(row);
        } catch (Exception e) {
            log.error("v2 审计写入失败 {}: {}", tool, e.getMessage());
        }
    }

    private void persist(String conv, Long userId, String role, String content) {
        if (content == null || content.isBlank()) return;
        try {
            AgentMessage m = new AgentMessage();
            m.setConversationId(conv);
            m.setUserId(userId == null ? 0L : userId);
            m.setRole(role);
            m.setContent(truncate(content, 2000));
            messageRepository.save(m);
        } catch (Exception e) {
            log.warn("v2 会话消息写入失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int limit) {
        if (s == null) return null;
        return s.length() <= limit ? s : s.substring(0, limit) + "…";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static String rootError(Throwable e) {
        String msg = rootMessage(e);
        return msg.length() > 60 ? msg.substring(0, 60) + "…" : msg;
    }
}
