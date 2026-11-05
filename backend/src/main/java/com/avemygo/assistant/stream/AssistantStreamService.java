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

    /**
     * 复读闸门：先攒够这么多字符再推给前端。
     * 若这段开头与上一条助手回复逐字相同，判定为"照抄上文"→ 整轮重试。
     * 40 字大约 0.1~0.3 秒的缓冲，换来「用户永远看不到复读」。
     */
    private static final int GUARD_PREFIX_CHARS = 40;

    /**
     * 判定"照抄"的最短连续相同片段（字符数）。
     * 为什么要它：实测模型会换个开头（"诶嘿嘿！"）然后整段照抄旧回答——
     * 只比开头 40 字会漏掉，必须看"有没有一段长片段与上一条回复相同"。
     */
    private static final int COPY_BLOCK_CHARS = 24;

    /** 闸门命中：模型开头照抄了上一条回复（内部信号，不对外暴露） */
    private static final class RepeatedAnswerException extends RuntimeException {
        RepeatedAnswerException() {
            super("assistant repeated previous answer");
        }
    }
    /** 同一工具连续失败多少次就停下来问用户 */
    private static final int MAX_SAME_TOOL_FAILS = 2;

    /**
     * 事实类意图：涉及成员 / 担当 / CV / 人数。
     * 这类问题 8B 模型极容易用训练记忆作答（实测编出"楽奈是鼓手、立希是键盘"，CV 也错），
     * 而粉丝向事实错误在这个站是红线 → 直接预取站内数据注入上下文，模型无从编造。
     */
    private static final Pattern MEMBER_FACT = Pattern.compile(".*(成员|担当|声优|CV|cv|几人|几个人|阵容).*");

    /** 纯寒暄：不挂工具，直接秒回（省掉一次工具决策的 3~6 秒） */
    private static final Pattern GREETING = Pattern.compile(
            "^(你好|您好|hi|hello|哈喽|嗨|在吗|在不在|早上好|晚上好|早|谢谢|多谢|感谢|再见|拜拜)[!！。.~～\\s]*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 系统提示词模板：%s = 角色显示名，%s = 人设卡。
     * 2026-11-01 修复：v2 此前用统一提示词，导致前端选「ANON」说话却像标准客服。
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是 BanG Dream! 粉丝站「BanG Dream! Live」的站内助手。
            【当前扮演的角色：%s】
            %s
            【风格与规则的优先级】（重要）
            · 语气、口癖、句式按上面的角色设定走，让用户听得出是谁在说话
            · 但涉及站内数据或操作时，必须说清「对象是谁、要改什么」；角色再沉默寡言，
              也要把对象名和改动内容完整说出来，不能因为"话少"漏掉关键信息
            【你能做的事】
            · 查站内数据：歌曲（搜索/MV/专辑/标签）、乐队、成员（含 CV）、演出、场次、票档与余票
            · 若调用方给了写工具：改 MV、改标签、上下架歌曲、票档上下架、改演出标题/场馆、改场次时间、删场次/演出
            【回答规则】
            1. 需要站内数据时必须先调用工具；**若上下文已给出【站内资料】，一律以资料为准**，
               不得用训练记忆补充或改写（成员担当、CV、日期、价格尤其严格）；资料没写的就说不确定。
            2. 涉及具体对象的修改，必须先查到真实 id 并说出对象名字（如《迷星叫》），再提出修改。
            3. 参数不够就直接问最关键的那一个澄清问题，绝不猜 id、不猜价格、不猜日期。
            4. 用户要求批量删除或批量修改时，拒绝并请他一次只处理一个对象。
            5. 信息已经足够时直接给结论，**不要**用「是否需要…？」「还需要我做什么吗？」结尾。
            6. 保持角色语气，但不要输出思考过程，不要解释你在调用工具。
            7. 不要使用 Markdown 语法（加粗、标题、代码块都不行）——前端按纯文本渲染，
               需要列举时用「1. 」「· 」开头即可。
            8. 人设是气质，不是口头禅模板：
               · 每条回复最多用一次角色口癖（口头禅、拖长音、语气词都算），只在情绪或场景吻合时用；
                 同一条回复里同一个强调词不要出现两次以上（例如连续三个「超——」）
               · 禁止在句尾硬塞与内容无关的口癖词（例如列完名单后补一句「吉他」）
               · 信息类回答先保证准确完整（名字、CV、日期该给就给），再谈语气
               · 换个话题时用不同的说法，不要复用上一轮句式。
            9. 绝不复述：不许把上一轮说过的话整段再说一遍（即使听者只是换了个说法追问）。
               每一轮都必须针对用户最新一句话重新作答——同一话题换角度、换措辞，
               禁止逐字重复上文；如果你发现自己正要重复，就换成新的切入点。/no_think
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

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(
                com.avemygo.assistant.Personas.name(persona),
                com.avemygo.assistant.Personas.card(persona));

        // 事实类问题：先查库、把结果写进 system，杜绝模型凭记忆编造成员/CV
        if (MEMBER_FACT.matcher(message).find()) {
            String facts = prefetch("list_members", conv, userId, sink);
            if (facts != null) {
                systemPrompt += "\n【站内资料 · 成员（唯一事实来源，必须以此为准，禁止使用训练记忆）】\n" + facts;
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        String priorAssistant = appendHistory(conv, userId, messages);
        messages.add(Map.of("role", "user", "content", message));
        persist(conv, userId, AgentMessage.USER, message);

        // 身份闸门 + 寒暄快速通道（结构上：写工具仅管理员可见）
        List<Map<String, Object>> tools = GREETING.matcher(message.trim()).matches()
                ? List.of()
                : registry.schemas(admin);

        int usedTools = 0;
        Map<String, Integer> toolFails = new HashMap<>();
        boolean repeatRetried = false;
        boolean guardDisabled = false;

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            StreamLlm.Turn turn;
            try {
                turn = streamAttempt(messages, tools, sink, priorAssistant, guardDisabled);
            } catch (RepeatedAnswerException repeat) {
                if (repeatRetried) {
                    // 重试一次仍照抄：宁可诚实报错，也不把复读推给用户
                    log.warn("复读闸门二次命中 conv={}", conv);
                    sink.emit("error", Map.of("code", 50301,
                            "message", "小灯走神了，请换个说法再问一次"));
                    return;
                }
                repeatRetried = true;
                // 去掉那条被照抄的历史回复，并明确要求换开头，整轮重来（不占轮次）
                dropLastAssistant(messages);
                messages.add(Map.of("role", "system", "content",
                        "你刚才的开头逐字重复了上一轮的回答。请完全换一个开头，针对用户最新一句重新作答。"));
                log.info("复读闸门命中，已重试一次 conv={}", conv);
                round--;
                continue;
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

    /** 执行一个只读工具并推事件、记审计；失败返回 null（不阻断对话） */
    private String prefetch(String toolName, String conv, Long userId, Sink sink) {
        AgentTool tool = registry.get(toolName);
        if (tool == null) return null;
        try {
            sink.emit("tool", Map.of("name", toolName, "status", "RUNNING",
                    "summary", runningSummary(toolName)));
            String result = tool.handler().execute(Map.of());
            audit(conv, userId, toolName, tool.risk().name(), "{}", result,
                    AgentToolCall.EXECUTED, null);
            sink.emit("tool", Map.of("name", toolName, "status", "OK",
                    "summary", truncate(result.replace("\n", " "), 80)));
            return result;
        } catch (Exception e) {
            audit(conv, userId, toolName, tool.risk().name(), "{}",
                    "执行失败：" + rootMessage(e), AgentToolCall.FAILED, null);
            sink.emit("tool", Map.of("name", toolName, "status", "FAIL", "summary", "查询失败"));
            return null;
        }
    }

    private String appendHistory(String conv, Long userId, List<Map<String, Object>> messages) {
        // 游客没有稳定身份，不读历史（否则不同游客只要 conversationId 相同就会串话）
        if (userId == null) return null;
        String lastAssistant = null;
        try {
            List<AgentMessage> recent = messageRepository.findByUserIdAndConversationIdOrderByIdDesc(
                    userId, conv, PageRequest.of(0, HISTORY_LIMIT));
            List<AgentMessage> chronological = new ArrayList<>(recent);
            java.util.Collections.reverse(chronological);
            // 历史去重（2026-11-05）：窗口内重复出现的同一段助手回复只保留第一条。
            // 注意不能只比较"相邻"——真实数据里两条重复回复之间夹着用户消息。
            // 不去重时小模型会看到"自己说过这句"，随后逐字照抄（实测连续三轮完全一致）。
            java.util.Set<String> seenAssistant = new java.util.HashSet<>();
            for (AgentMessage m : chronological) {
                boolean isUser = AgentMessage.USER.equals(m.getRole());
                String text = MarkdownStripper.strip(m.getContent());
                if (!isUser) {
                    String key = normalizeForCompare(text);
                    if (!key.isEmpty() && !seenAssistant.add(key)) {
                        continue;              // 同一段回答已出现过，丢弃后续副本
                    }
                }
                messages.add(Map.of("role", isUser ? "user" : "assistant", "content", text));
                if (!isUser) lastAssistant = text;   // 供复读闸门比对
            }
        } catch (Exception e) {
            log.warn("v2 历史载入失败: {}", e.getMessage());
        }
        return lastAssistant;
    }

    /**
     * 单轮流式调用，带「复读闸门」。
     *
     * 闸门逻辑：正文先攒够 {@link #GUARD_PREFIX_CHARS} 个字符再推给前端；
     * 攒够后与上一条助手回复的开头比对，若逐字相同 → 抛 {@link RepeatedAnswerException}
     * 让上层整轮重试。用户因此永远不会看到"把上一轮的话再贴一遍"。
     *
     * @param guardDisabled 已重试过则关闭闸门，避免无限循环
     */
    private StreamLlm.Turn streamAttempt(List<Map<String, Object>> messages,
                                         List<Map<String, Object>> tools,
                                         Sink sink,
                                         String priorAssistant,
                                         boolean guardDisabled) throws Exception {
        MarkdownStripper stripper = new MarkdownStripper();   // 流式出口的 markdown 清洗器
        boolean guarded = guardDisabled || priorAssistant == null || priorAssistant.isBlank();
        String priorKey = guarded ? null : normalizeForCompare(priorAssistant);
        StringBuilder buffer = new StringBuilder();
        boolean[] passed = {guarded};

        StreamLlm.Turn turn = streamLlm.stream(messages, tools, new StreamLlm.Listener() {
            @Override
            public void onThinking() {
                sink.emit("status", Map.of("phase", "thinking"));
            }

            @Override
            public void onContent(String delta) {
                String clean = stripper.push(delta);
                if (clean.isEmpty()) return;
                if (passed[0]) {
                    sink.emit("delta", Map.of("text", clean));
                    return;
                }
                buffer.append(clean);
                // 注意：必须按"归一化后的有效字数"判断，不能按原始长度——
                // 标点/空白占了 40 个原始字符时，有效字数只有 30 出头，比对会永远不成立。
                String headKey = normalizeForCompare(buffer.toString());
                int need = Math.min(GUARD_PREFIX_CHARS, priorKey.length());
                if (headKey.length() < need) {
                    return;   // 还没攒够有效字符，先扣着不推
                }
                boolean sameHead = headKey.regionMatches(0, priorKey, 0, need);
                boolean copiedBlock = longestCommonSubstring(headKey, priorKey) >= COPY_BLOCK_CHARS;
                if (sameHead || copiedBlock) {
                    throw new RepeatedAnswerException();
                }
                passed[0] = true;
                sink.emit("delta", Map.of("text", buffer.toString()));
                buffer.setLength(0);
            }

            @Override
            public void onToolCall(String id, String name, String args) {
                // 工具调用在正文之后才处理，这里不推事件（避免"正在调用"闪一下）
            }
        });

        // 回答短于闸门长度时缓冲还没推出去：这里补一次判断 + 冲刷，避免前端白屏
        if (!passed[0]) {
            String rest = buffer.toString();
            String restKey = normalizeForCompare(rest);
            if (!restKey.isEmpty()
                    && (restKey.regionMatches(0, priorKey, 0, Math.min(restKey.length(), priorKey.length()))
                        || longestCommonSubstring(restKey, priorKey) >= COPY_BLOCK_CHARS)) {
                throw new RepeatedAnswerException();
            }
            if (!rest.isEmpty()) sink.emit("delta", Map.of("text", rest));
        }
        return turn;
    }

    /** 最长公共子串长度（动态规划，文本量很小，O(n*m) 足够） */
    private static int longestCommonSubstring(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int[] prev = new int[b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    cur[j] = prev[j - 1] + 1;
                    if (cur[j] > best) best = cur[j];
                }
            }
            prev = cur;
        }
        return best;
    }

    /** 移除历史里最后一条助手消息（复读重试时用，避免再次诱导照抄） */
    private static void dropLastAssistant(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).get("role"))) {
                messages.remove(i);
                return;
            }
        }
    }

    /** 去掉空白与常见标点后比较，避免"看起来一样"的回复逃过去重 */
    private static String normalizeForCompare(String text) {
        return text == null ? "" : text.replaceAll("[\\s，。！？、…~～—\\(\\)（）]", "");
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
        if (userId == null) return;   // 游客不落库：没有身份就无法隔离，宁可不存
        try {
            // 幂等：3 秒内同会话、同角色、同内容只写一条。
            // 场景（2026-11-05 实测）：v2 流式失败 → 前端降级 v1 兜底，
            // 两条链路各写一次用户消息，历史里出现两条相同的「我喜欢你」，污染上下文。
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusSeconds(3);
            // 取最近 4 条：最后一条往往是助手回复，只看 1 条会漏掉紧邻其前的同一条用户消息
            boolean duplicated = messageRepository
                    .findByUserIdAndConversationIdOrderByIdDesc(userId, conv, PageRequest.of(0, 4))
                    .stream()
                    .anyMatch(m -> role.equals(m.getRole())
                            && content.equals(m.getContent())
                            && m.getCreatedAt() != null
                            && m.getCreatedAt().isAfter(since));
            if (duplicated) {
                log.debug("跳过重复会话消息 conv={} role={}", conv, role);
                return;
            }
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
