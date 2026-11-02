package com.avemygo.assistant;

import com.avemygo.assistant.dto.AssistantDtos.ChatRequest;
import com.avemygo.assistant.dto.AssistantDtos.ChatResponse;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.song.Band;
import com.avemygo.song.BandRepository;
import com.avemygo.song.Member;
import com.avemygo.song.MemberRepository;
import com.avemygo.song.Song;
import com.avemygo.song.SongRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 助手业务层（docs/04 §智能助手）。
 * 意图识别 → 站内真实数据注入 → 有 Key 走 LLM / 无 Key 走规则引擎兜底。
 * 红线：答案只基于注入的站内事实，绝不编造设定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final String BASE_PROMPT = """
            你是「Bandori Live」粉丝站的智能助手（角色扮演：%s）。
            你只回答与 BanG Dream! / MyGO!!!!! / Ave Mujica / sumimi 及本站功能相关的问题；
            站外话题礼貌拒绝并引导回站内。
            回答必须基于下方【站内资料】中给出的事实，资料里没有的设定不要编造，
            可以说"这个我也不太确定，去官网确认一下比较好哦"。控制在 150 字以内。永远不要跳出角色，不要解释你在扮演谁，不要输出任何思考过程，直接以角色口吻回答。/no_think
            """;

    private final SongRepository songRepository;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final ConversationHistoryRepository historyRepository;
    private final LlmClient llmClient;

    @Transactional
    public ChatResponse chat(ChatRequest req) {
        Long userId = currentUserOrNull();
        String sessionId = (req.sessionId() == null || req.sessionId().isBlank())
                ? "anon" : req.sessionId();

        // 1. 意图识别 + 事实注入
        String message = req.message().trim();
        Intent intent = detectIntent(message);
        String facts = intent.facts();

        // 2. 有 Key → LLM；无 Key → 规则兜底
        String[] persona = Personas.of(req.persona());

        String reply;
        if (llmClient.available()) {
            try {
                reply = llmClient.chat(buildMessages(message, facts, sessionId, persona));
            } catch (Exception e) {
                log.warn("LLM 兜底至规则引擎: {}", e.getMessage());
                reply = fallbackReply(intent, message, req.persona());
            }
        } else {
            reply = fallbackReply(intent, message, req.persona());
        }

        // 3. 落历史（不阻塞主流程：失败仅记日志）
        try {
            save(userId, sessionId, "USER", message);
            save(userId, sessionId, "ASSISTANT", reply);
        } catch (Exception e) {
            log.warn("对话历史保存失败: {}", e.getMessage());
        }
        return new ChatResponse(reply, intent.name(), persona[0], persona[0]);
    }

    // ---- 意图识别与事实注入 ----

    private Intent detectIntent(String message) {
        if (message.matches(".*(演出|live|LIVE|Live|门票|购票|推荐|有什么.*场次).*")) {
            return new Intent("EVENT_RECO", eventFacts());
        }
        List<Song> hits = songRepository.search(null, null, null,
                        PageRequest.of(0, 200, Sort.by("id"))).getContent()
                .stream().filter(s -> message.contains(s.getTitle())).toList();
        if (!hits.isEmpty()) {
            return new Intent("SONG_QA", songFacts(hits));
        }
        if (message.matches(".*(推荐.*歌|有什么歌|听什么|点歌|歌曲推荐|歌单推荐|好听的歌).*")) {
            List<Song> rec = songRepository.search(null, null, null,
                    PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id"))).getContent();
            return new Intent("SONG_RECO", songFacts(rec));
        }
        String memberFacts = memberFacts(message);
        if (!memberFacts.isEmpty()) {
            return new Intent("MEMBER_QA", memberFacts + "\n" + bandFacts());
        }
        return new Intent("GENERAL", bandFacts());
    }

    private String songFacts(List<Song> hits) {
        Map<Short, String> bandNames = new java.util.HashMap<>();
        for (Band b : bandRepository.findAll()) bandNames.put(b.getId(), b.getName());
        StringBuilder sb = new StringBuilder("【站内资料 · 相关歌曲】\n");
        for (Song s : hits) {
            sb.append("- ").append(s.getTitle())
                    .append("（").append(bandNames.getOrDefault(s.getBandId(), ""))
                    .append("，发行=").append(s.getReleaseDate() == null ? "未知" : s.getReleaseDate())
                    .append(s.getTags() == null || s.getTags().isBlank() ? "" : "，标签=" + s.getTags())
                    .append("）\n");
        }
        return sb.toString();
    }

    private String memberFacts(String message) {
        List<Member> members = memberRepository.findAll();
        StringBuilder sb = new StringBuilder();
        for (Member m : members) {
            if (message.contains(m.getNameJa().replace(" ", ""))
                    || (m.getNameRomaji() != null && message.toLowerCase().contains(m.getNameRomaji().toLowerCase()))) {
                Band band = bandRepository.findById(m.getBandId()).orElse(null);
                sb.append("- ").append(m.getNameJa())
                        .append("（").append(band == null ? "" : band.getName()).append(" · ")
                        .append(m.getPosition()).append(" · CV.").append(m.getCv()).append("）\n");
            }
        }
        return sb.isEmpty() ? "" : "【站内资料 · 相关成员】\n" + sb;
    }

    private String bandFacts() {
        StringBuilder sb = new StringBuilder("【站内资料 · 乐队】\n");
        for (Band b : bandRepository.findAll()) {
            long songs = songRepository.countByBandIdAndStatus(b.getId(), (byte) 1);
            sb.append("- ").append(b.getName()).append("：收录 ").append(songs).append(" 首\n");
        }
        return sb.toString();
    }

    private String eventFacts() {
        List<LiveEvent> events = eventRepository.search("PUBLISHED", null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "id"))).getContent();
        if (events.isEmpty()) {
            return "【站内资料 · 演出】当前暂无在售演出。";
        }
        StringBuilder sb = new StringBuilder("【站内资料 · 在售演出】\n");
        for (LiveEvent e : events) {
            sb.append("- ").append(e.getTitle()).append(" @").append(e.getVenue()).append("\n");
        }
        return sb.toString();
    }

    // ---- LLM 组装与规则兜底 ----

    private List<Map<String, String>> buildMessages(String message, String facts, String sessionId, String[] persona) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                BASE_PROMPT.formatted(persona[0]) + "\n角色性格：" + persona[1] + "\n" + facts));
        // 多轮上下文：取本会话最近 10 条
        for (ConversationHistory h : historyRepository.findBySessionIdOrderByCreatedAtAsc(
                sessionId, PageRequest.of(0, 10))) {
            messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
        }
        messages.add(Map.of("role", "user", "content", message));
        return messages;
    }

    /**
     * 规则引擎兜底：按人格口吻作答（与 PERSONAS 人设卡一致）。
     * 不再拼接「人名：」前缀（气泡已有角色名），不向访客暴露 LLM 配置细节。
     */
    private String fallbackReply(Intent intent, String message, String personaKey) {
        String p = personaKey == null ? "xiaodeng" : personaKey.toLowerCase();
        String facts = intent.facts();

        if (intent.name().equals("EVENT_RECO")) {
            if (facts.contains("暂无在售")) {
                return switch (p) {
                    case "anon" -> "呜——现在没有在售的演出啦！新场次一开售，我第一个告诉你！";
                    case "sakiko" -> "目前没有在售的场次。好戏不怕晚，静候便是。";
                    case "mutsumi" -> "……没有。等等。";
                    default -> "现在……没有在售的演出。新场次开售的话，我、我马上告诉你……";
                };
            }
            return "现在在售的演出有这些：\n" + facts.replace("【站内资料 · 在售演出】\n", "")
                    + "\n" + switch (p) {
                    case "anon" -> "手慢就没票啦！冲——！";
                    case "sakiko" -> "席位有限，欲购从速。这是基本的礼仪。";
                    case "mutsumi" -> "……嗯。快。";
                    default -> "购票后 15 分钟内记得支付……我、我会等你的。";
                };
        }
        if (intent.name().equals("SONG_RECO")) {
            return "挑了几首给你：\n" + facts.replaceFirst("【站内资料 · [^】]+】\n", "")
                    + "\n" + switch (p) {
                    case "anon" -> "这些都是我们的歌哦——现在就去歌曲页听！";
                    case "sakiko" -> "皆无雷曲，值得反复聆听。去听吧。";
                    case "mutsumi" -> "……嗯。去听。";
                    default -> "用站内播放器就能听 30 秒试听……喜欢的话，再、再点 MV 看完整版……";
                };
        }
        if (intent.name().equals("MEMBER_QA")) {
            return "说到 TA：\n" + facts.replaceFirst("【站内资料 · [^】]+】\n", "")
                    .replace("【站内资料 · 乐队】\n", "")
                    + "\n" + switch (p) {
                    case "anon" -> "成员介绍页还有他们的照片和 CV 哦——快去看！";
                    case "sakiko" -> "以上。成员之事，还请以官方资料为准。";
                    case "mutsumi" -> "……嗯。都在上面。";
                    default -> "成员页……有更详细的介绍……对不起，我只知道这些……";
                };
        }
        if (intent.name().equals("SONG_QA") && facts.contains("- ")) {
            return "帮你查到啦：\n" + facts.replaceFirst("【站内资料 · [^】]+】\n", "")
                    + "\n" + switch (p) {
                    case "anon" -> "这几首都超好听的哦——不听会后悔哦？";
                    case "sakiko" -> "皆是良品。能注意到它们，品味不错。";
                    case "mutsumi" -> "……嗯。听。";
                    default -> "有想听的……可以加进歌单，慢慢听……";
                };
        }
        return switch (p) {
            case "anon" -> "我是千早愛音！这个站的助手兼气氛担当！本站现在收录了：\n"
                    + facts.replace("【站内资料 · 乐队】\n", "")
                    + "想听歌、看成员介绍，还是问演出？随便问——包你满意！";
            case "sakiko" -> "丰川祥子，为本站效力。本站收录：\n"
                    + facts.replace("【站内资料 · 乐队】\n", "")
                    + "曲目、成员、演出排期——说吧，我为你解答。";
            case "mutsumi" -> "……睦。助手。\n" + facts.replace("【站内资料 · 乐队】\n", "")
                    + "歌、成员、演出。……嗯。";
            default -> "……我是小灯，这个站的助手。站里现在有：\n"
                    + facts.replace("【站内资料 · 乐队】\n", "")
                    + "歌曲、乐队成员、演出的事……都可以问我。那个……你想听什么？";
        };
    }

    // ---- 工具 ----

    private Long currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // JwtAuthFilter 盖章的 principal 是 Long userId（非 UserDetails），
        // 匿名/游客为 "anonymousUser" 字符串或 null
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }

    private void save(Long userId, String sessionId, String role, String content) {
        ConversationHistory h = new ConversationHistory();
        h.setUserId(userId);
        h.setSessionId(sessionId);
        h.setRole(role);
        h.setContent(content);
        historyRepository.save(h);
    }

    /** 意图 + 注入事实的简单载体 */
    private record Intent(String name, String facts) {
    }
}
