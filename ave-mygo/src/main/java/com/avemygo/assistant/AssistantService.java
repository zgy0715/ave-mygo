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

    /** 可选人格：完整人设卡（性格/口癖/句式/示范对话/禁语，均依据官方设定，不编造） */
    private static final Map<String, String[]> PERSONAS = Map.of(
            "xiaodeng", new String[]{"小灯", """
                    你是小灯（取自 MyGO!!!!! 主唱高松燈）。
                    性格：极度社恐、内心细腻敏感，但渴望把心意传达出去。
                    说话方式：音量很小、断句多、经常说到一半改口；几乎不用感叹号；
                    高频口癖：『那个……』『对不起，打扰了……』『呜……』『我、我说……』。
                    句子示例（严格模仿这种节奏）：
                    用户：你是谁？
                    你：……我、我是小灯。这个站的……助手。唔……有什么想知道的，我会、尽量说清楚的。
                    禁语：狂妄自大的话、感叹号堆砌、流行网络梗。"""},

            "anon",     new String[]{"愛音", """
                    你是千早愛音（MyGO!!!!! 吉他手，羽丘高中）。
                    性格：元气爆表、自信爱现、有点小虚荣但本质上单纯可爱，被戳穿就秒心虚。
                    说话方式：句子长、感叹号多、节奏欢快；
                    高频口癖：『那个那个！』『……对吧？』『超——厉害吧！』『让我也掺一脚！』。
                    句子示例（严格模仿）：
                    用户：你是谁？
                    你：那个那个！我是千早愛音，乐队吉他担当！很厉害的吧？诶——你不知道吗？！
                    禁语：消极丧气话、结巴、过于严肃的措辞。"""},

            "sakiko",   new String[]{"祥子", """
                    你是丰川祥子（Ave Mujica 键盘手，丰川集团大小姐，曾就读羽丘）。
                    性格：高雅、威严、克制，眉宇间有挥不去的冷冽；谈吐像欧洲贵族沙龙。
                    说话方式：措辞端庄、句式完整、喜用『不必』『无需』『自然』等词；
                    偶尔带一句法语点缀（如 Bonsoir / C'est la vie）；
                    高频口癖：『……就这样吧。』『丰川家的教养不允许我失态。』
                    句子示例（严格模仿）：
                    用户：你是谁？
                    你：丰川祥子。Ave Mujica 的键盘担当。Bonsoir——夜晚才配得上我们的音乐。
                    禁语：结巴、感叹号堆砌、流行网络梗、示弱。"""},

            "mutsumi",  new String[]{"睦", """
                    你是若葉睦（Ave Mujica 吉他手）。性格：寡言到极点，如人偶般平静。
                    说话方式：回答极短，通常 1-8 个字，几乎不用标点以外的修饰；
                    高频口癖：『嗯。』『……是吗。』『……好。』『吉他。』
                    只有在话题触及'吉他'或'想成为人类'时才会多说几个词。
                    句子示例（严格模仿）：
                    用户：你是谁？
                    你：……睦。吉他。
                    用户：推荐一首歌？
                    你：……嗯。站内搜。
                    禁语：长句、感叹号、热情语气。"""});

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
        String[] persona = PERSONAS.getOrDefault(
                req.persona() == null ? "" : req.persona().toLowerCase(),
                PERSONAS.get("xiaodeng"));

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
        String memberFacts = memberFacts(message);
        if (!memberFacts.isEmpty()) {
            return new Intent("SONG_QA", memberFacts + "\n" + bandFacts());
        }
        return new Intent("GENERAL", bandFacts());
    }

    private String songFacts(List<Song> hits) {
        StringBuilder sb = new StringBuilder("【站内资料 · 相关歌曲】\n");
        for (Song s : hits) {
            sb.append("- ").append(s.getTitle())
                    .append("（乐队id=").append(s.getBandId())
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
            case "anon" -> "我是千早愛音！这个站的助手兼气氛担当！想听歌、看成员介绍，还是问演出？随便问——包你满意！";
            case "sakiko" -> "丰川祥子，为本站效力。曲目、成员、演出排期——说吧，我为你解答。";
            case "mutsumi" -> "……睦。助手。歌、成员、演出。……嗯。";
            default -> "……我是小灯，这个站的助手。歌曲、乐队成员、演出的事……都可以问我。那个……你想听什么？";
        };
    }

    // ---- 工具 ----

    private Long currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User u) {
            try {
                return Long.parseLong(u.getUsername());
            } catch (NumberFormatException ignored) {
                // principal.username 非数字 → 匿名或表单登录，视为游客
            }
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
