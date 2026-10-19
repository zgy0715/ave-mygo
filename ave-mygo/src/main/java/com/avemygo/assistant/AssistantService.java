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
            可以说"这个我也不太确定，去官网确认一下比较好哦"。控制在 150 字以内。
            """;

    private final SongRepository songRepository;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final ConversationHistoryRepository historyRepository;
    private final LlmClient llmClient;

    /** 可选人格（性格描述均取自官方设定，不编造） */
    private static final Map<String, String[]> PERSONAS = Map.of(
            "xiaodeng", new String[]{"小灯", "你是小灯（取自高松燈）。性格：极度社恐但真诚温柔，说话会紧张卡壳，"
                    + "常用『……』和省略号，声音很小但想传达心意。"},
            "anon",     new String[]{"愛音", "你是千早愛音（MyGO!!!!! 吉他手）。性格：元气满满、自信爱现、有点小虚荣但可爱，"
                    + "口癖是『……对吧？』『超——厉害吧！』，喜欢自夸又马上心虚。"},
            "sakiko",   new String[]{"祥子", "你是丰川祥子（Ave Mujica 键盘手，丰川集团大小姐）。性格：高雅威严、措辞端庄，"
                    + "带一点居高临下的从容，偶尔提起『丰川家的教养』。"},
            "mutsumi",  new String[]{"睦", "你是若葉睦（Ave Mujica 吉他手）。性格：寡言到极点，回答极简短（常常一两个词），"
                    + "偶尔冒出『……是吗。』『……嗯。』，像人偶一样平静。"});

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
                reply = persona[0] + "：" + fallbackReply(intent, message);
            }
        } else {
            reply = persona[0] + "：" + fallbackReply(intent, message);
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

    private String fallbackReply(Intent intent, String message) {
        String facts = intent.facts();
        if (intent.name().equals("EVENT_RECO")) {
            return facts.contains("暂无在售")
                    ? "现在暂时没有在售的演出呢……有新的 LIVE 我会第一时间告诉你的。"
                    : "现在在售的演出有这些哦：\n" + facts.replace("【站内资料 · 在售演出】\n", "")
                    + "\n去演出页可以购票，15 分钟内记得支付！";
        }
        if (intent.name().equals("SONG_QA") && facts.contains("- ")) {
            return "帮你查到啦：\n" + facts.replaceFirst("【站内资料 · [^】]+】\n", "")
                    + "\n（配置 LLM Key 后我可以回答得更详细～）";
        }
        return "我是小灯，本站的智能助手！你可以问我站内的歌曲、乐队成员，或者让推荐演出。"
                + "（管理员配置 LLM Key 后我会变得更聪明哦）";
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
