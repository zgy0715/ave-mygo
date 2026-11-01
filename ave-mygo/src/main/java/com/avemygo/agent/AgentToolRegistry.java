package com.avemygo.agent;

import com.avemygo.admin.AdminEventService;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypePatch;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSession;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.TicketType;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.song.Band;
import com.avemygo.song.BandRepository;
import com.avemygo.song.Member;
import com.avemygo.song.MemberRepository;
import com.avemygo.song.Song;
import com.avemygo.song.SongRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 工具注册表（docs/10 §4）：把既有管理端能力包装成 LLM 可调用的工具。
 * 设计要点：
 *  - 单一 LinkedHashMap，输出顺序稳定（便于测试与提示词缓存）
 *  - 启动时自检：名称唯一、参数 Schema 必须是 object、WRITE 工具必须有说明
 *  - 工具内部直接调用服务层/仓储层（进程内），不做 HTTP 往返
 */
@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    private final SongRepository songRepository;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final LiveSessionRepository sessionRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final AdminEventService adminEventService;

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        // ---------- 只读工具 ----------
        register(new AgentTool("search_songs",
                "按歌名或乐队名模糊搜索站内歌曲，返回 id/曲名/乐队/MV 有无。适合在改数据前先确认歌曲 id。",
                AgentTool.Risk.READ,
                schema(Map.of(
                        "keyword", Map.of("type", "string", "description", "歌名或乐队名关键词，可省略"),
                        "limit", Map.of("type", "integer", "description", "返回条数，默认 5，最大 10")
                ), List.of()),
                args -> {
                    String keyword = optStr(args, "keyword");
                    int limit = Math.min((int) optLong(args, "limit", 5L), 10);
                    var page = songRepository.search(null, blankToNull(keyword), null,
                            PageRequest.of(0, limit, Sort.by("id")));
                    if (page.isEmpty()) return "没有找到匹配的歌曲。";
                    Map<Short, String> bands = bandNames();
                    List<String> lines = new ArrayList<>();
                    for (Song s : page.getContent()) {
                        lines.add("- id=%d 《%s》 %s MV=%s".formatted(
                                s.getId(), s.getTitle(),
                                bands.getOrDefault(s.getBandId(), ""),
                                s.getMvUrl() == null || s.getMvUrl().isBlank() ? "无" : "有"));
                    }
                    return "共 %d 首：\n%s".formatted(page.getNumberOfElements(), String.join("\n", lines));
                }));

        register(new AgentTool("get_song",
                "查看单首歌曲的完整信息（乐队、发行日期、标签、音源与 MV 地址）。",
                AgentTool.Risk.READ,
                schema(Map.of("songId", Map.of("type", "integer", "description", "歌曲 id")),
                        List.of("songId")),
                args -> {
                    long id = reqLong(args, "songId");
                    Song s = songRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("歌曲 id=" + id + " 不存在"));
                    return """
                            id=%d 《%s》
                            乐队：%s
                            发行：%s
                            标签：%s
                            音源：%s
                            MV：%s""".formatted(s.getId(), s.getTitle(),
                            bandNames().getOrDefault(s.getBandId(), ""),
                            s.getReleaseDate() == null ? "未知" : s.getReleaseDate(),
                            s.getTags() == null || s.getTags().isBlank() ? "无" : s.getTags(),
                            s.getAudioUrl() == null ? "无" : s.getAudioUrl(),
                            s.getMvUrl() == null ? "无" : s.getMvUrl());
                }));

        // 刻意不提供 status 参数：8B 模型会把「所有演出」自作主张加成 status=PUBLISHED，
        // 漏掉已结束的场次。去掉参数 = 结构上不可能过滤错（每条记录自带状态，用户自己看）
        register(new AgentTool("list_events",
                "列出最近 10 场演出（管理视角，含草稿/已发布/已结束，每条都带状态）。无需任何参数。",
                AgentTool.Risk.READ,
                schema(Map.of(), List.of()),
                args -> {
                    var page = eventRepository.searchAdmin(null, null,
                            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));
                    if (page.isEmpty()) return "当前没有演出记录。";
                    List<String> lines = new ArrayList<>();
                    for (LiveEvent e : page.getContent()) {
                        lines.add("- id=%d 《%s》 %s @%s".formatted(
                                e.getId(), e.getTitle(), e.getStatus(), e.getVenue()));
                    }
                    return "共 %d 场：\n%s".formatted(page.getNumberOfElements(), String.join("\n", lines));
                }));

        register(new AgentTool("list_sessions",
                "查看某个演出的全部场次及其票档（含票档 id、价格、库存、上下架状态）。",
                AgentTool.Risk.READ,
                schema(Map.of("eventId", Map.of("type", "integer", "description", "演出 id")),
                        List.of("eventId")),
                args -> {
                    long eventId = reqLong(args, "eventId");
                    List<LiveSession> sessions = sessionRepository.findByEventIdOrderByStartTimeAsc(eventId);
                    if (sessions.isEmpty()) return "该演出还没有场次。";
                    StringBuilder sb = new StringBuilder();
                    for (LiveSession s : sessions) {
                        sb.append("- 场次 id=%d %s %s 状态=%s\n".formatted(
                                s.getId(), s.getName(), s.getStartTime(), s.getStatus()));
                        for (TicketType t : ticketTypeRepository.findBySessionIdOrderByIdAsc(s.getId())) {
                            sb.append("    票档 id=%d %s ¥%s 库存%d/已售%d 状态=%s\n".formatted(
                                    t.getId(), t.getName(), t.getPrice(),
                                    t.getTotalStock(), t.getSoldCount(), t.getStatus()));
                        }
                    }
                    return sb.toString();
                }));

        register(new AgentTool("list_members",
                "列出乐队成员（姓名 / 担当 / CV）。可按乐队名筛选，如 MyGO、Ave Mujica。",
                AgentTool.Risk.READ,
                schema(Map.of("band", Map.of("type", "string",
                        "description", "乐队名关键词，可省略")), List.of()),
                args -> {
                    String band = optStr(args, "band");
                    List<String> lines = new ArrayList<>();
                    for (Member m : memberRepository.findAll()) {
                        if (m.getIsDeleted() != null && m.getIsDeleted() != 0) continue;
                        String bandName = bandNames().getOrDefault(m.getBandId(), "");
                        if (band != null && !band.isBlank() && !bandName.contains(band)) continue;
                        lines.add("- %s（%s · %s · CV.%s）".formatted(
                                m.getNameJa(), bandName, m.getPosition(), m.getCv()));
                    }
                    return lines.isEmpty() ? "没有找到匹配的成员。" : "共 %d 位：\n%s".formatted(lines.size(), String.join("\n", lines));
                }));

        register(new AgentTool("list_bands",
                "列出站内收录的乐队及各自收录曲数。无需参数。",
                AgentTool.Risk.READ,
                schema(Map.of(), List.of()),
                args -> {
                    List<String> lines = new ArrayList<>();
                    for (Band b : bandRepository.findAll()) {
                        long songs = songRepository.countByBandIdAndStatus(b.getId(), (byte) 1);
                        lines.add("- id=%d %s（收录 %d 首）".formatted(b.getId(), b.getName(), songs));
                    }
                    return "共 %d 支乐队：\n%s".formatted(lines.size(), String.join("\n", lines));
                }));

        register(new AgentTool("get_event",
                "查看某个演出的详情：状态、场馆、场次数、票档数与余票合计。",
                AgentTool.Risk.READ,
                schema(Map.of("eventId", Map.of("type", "integer", "description", "演出 id")),
                        List.of("eventId")),
                args -> {
                    long id = reqLong(args, "eventId");
                    LiveEvent e = eventRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("演出 id=" + id + " 不存在"));
                    List<LiveSession> sessions = sessionRepository.findByEventIdOrderByStartTimeAsc(id);
                    int ticketCount = 0;
                    int remaining = 0;
                    for (LiveSession s : sessions) {
                        List<TicketType> tts = ticketTypeRepository.findBySessionIdOrderByIdAsc(s.getId());
                        ticketCount += tts.size();
                        for (TicketType t : tts) remaining += Math.max(0, t.getTotalStock() - t.getSoldCount());
                    }
                    return """
                            id=%d 《%s》
                            状态：%s
                            场馆：%s
                            场次：%d 个
                            票档：%d 个，余票合计 %d""".formatted(e.getId(), e.getTitle(), e.getStatus(),
                            e.getVenue(), sessions.size(), ticketCount, remaining);
                }));

        // ---------- 写工具（必须人工确认） ----------
        register(new AgentTool("update_song_mv",
                "修改指定歌曲的 MV 地址（B 站播放页或 iframe 链接）。写操作，执行前需人工确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of(
                        "songId", Map.of("type", "integer", "description", "歌曲 id"),
                        "mvUrl", Map.of("type", "string", "description", "新的 MV 地址，http(s) 开头")
                ), List.of("songId", "mvUrl")),
                args -> {
                    long id = reqLong(args, "songId");
                    String mvUrl = reqStr(args, "mvUrl").trim();
                    if (!(mvUrl.startsWith("http://") || mvUrl.startsWith("https://"))) {
                        throw new IllegalArgumentException("mvUrl 必须是 http(s) 链接");
                    }
                    Song s = songRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("歌曲 id=" + id + " 不存在"));
                    String old = s.getMvUrl();
                    s.setMvUrl(mvUrl);
                    songRepository.save(s);
                    return "已更新《%s》的 MV 地址：%s → %s".formatted(s.getTitle(),
                            old == null ? "（空）" : old, mvUrl);
                }));

        register(new AgentTool("set_ticket_status",
                "下架或恢复某个票档：status=0 下架（不可再售），status=1 恢复。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of(
                        "ticketTypeId", Map.of("type", "integer", "description", "票档 id"),
                        "status", Map.of("type", "integer", "description", "0 下架 / 1 恢复")
                ), List.of("ticketTypeId", "status")),
                args -> {
                    long id = reqLong(args, "ticketTypeId");
                    long status = reqLong(args, "status");
                    if (status != 0 && status != 1) throw new IllegalArgumentException("status 只能是 0 或 1");
                    var view = adminEventService.updateTicketType(id,
                            new TicketTypePatch(null, null, null, null, (byte) status));
                    return "票档「%s」已%s".formatted(view.name(), status == 1 ? "恢复上架" : "下架");
                }));

        register(new AgentTool("delete_session",
                "删除（软删）某个场次，其票档与曲目单会一并移除。存在未完成订单时会被拒绝。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of("sessionId", Map.of("type", "integer", "description", "场次 id")),
                        List.of("sessionId")),
                args -> {
                    long id = reqLong(args, "sessionId");
                    adminEventService.deleteSession(id);
                    return "场次 id=%d 已删除（级联软删票档与曲目单）".formatted(id);
                }));

        register(new AgentTool("delete_event",
                "删除（软删）某个演出。存在待支付/已支付订单时会被拒绝。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of("eventId", Map.of("type", "integer", "description", "演出 id")),
                        List.of("eventId")),
                args -> {
                    long id = reqLong(args, "eventId");
                    adminEventService.deleteEvent(id);
                    return "演出 id=%d 已删除".formatted(id);
                }));

        register(new AgentTool("update_song_tags",
                "修改歌曲标签（逗号分隔，如「Live,MyGO」）。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of(
                        "songId", Map.of("type", "integer", "description", "歌曲 id"),
                        "tags", Map.of("type", "string", "description", "新标签，逗号分隔，可为空字符串")
                ), List.of("songId", "tags")),
                args -> {
                    long id = reqLong(args, "songId");
                    String tags = reqStr(args, "tags").trim();
                    Song s = songRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("歌曲 id=" + id + " 不存在"));
                    String old = s.getTags();
                    s.setTags(tags);
                    songRepository.save(s);
                    return "已更新《%s》的标签：%s → %s".formatted(s.getTitle(),
                            old == null || old.isBlank() ? "（空）" : old, tags.isBlank() ? "（空）" : tags);
                }));

        register(new AgentTool("set_song_status",
                "歌曲上架或下架：status=1 上架，status=0 下架。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of(
                        "songId", Map.of("type", "integer", "description", "歌曲 id"),
                        "status", Map.of("type", "integer", "description", "1 上架 / 0 下架")
                ), List.of("songId", "status")),
                args -> {
                    long id = reqLong(args, "songId");
                    long status = reqLong(args, "status");
                    if (status != 0 && status != 1) throw new IllegalArgumentException("status 只能是 0 或 1");
                    Song s = songRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("歌曲 id=" + id + " 不存在"));
                    s.setStatus((byte) status);
                    songRepository.save(s);
                    return "《%s》已%s".formatted(s.getTitle(), status == 1 ? "上架" : "下架");
                }));

        register(new AgentTool("update_event_info",
                "修改演出的标题或场馆（只传要改的字段）。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of(
                        "eventId", Map.of("type", "integer", "description", "演出 id"),
                        "title", Map.of("type", "string", "description", "新标题，可省略"),
                        "venue", Map.of("type", "string", "description", "新场馆，可省略")
                ), List.of("eventId")),
                args -> {
                    long id = reqLong(args, "eventId");
                    String title = optStr(args, "title");
                    String venue = optStr(args, "venue");
                    if ((title == null || title.isBlank()) && (venue == null || venue.isBlank())) {
                        throw new IllegalArgumentException("至少要提供 title 或 venue 之一");
                    }
                    LiveEvent e = eventRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("演出 id=" + id + " 不存在"));
                    StringBuilder sb = new StringBuilder("已更新演出 id=" + id + "：");
                    if (title != null && !title.isBlank()) {
                        sb.append("标题「%s」→「%s」 ".formatted(e.getTitle(), title));
                        e.setTitle(title);
                    }
                    if (venue != null && !venue.isBlank()) {
                        sb.append("场馆「%s」→「%s」".formatted(e.getVenue(), venue));
                        e.setVenue(venue);
                    }
                    eventRepository.save(e);
                    return sb.toString().trim();
                }));

        register(new AgentTool("update_session_time",
                "修改场次的开演与结束时间，格式 yyyy-MM-dd HH:mm（如 2026-11-14 18:00）。写操作，需确认。",
                AgentTool.Risk.WRITE,
                schema(Map.of(
                        "sessionId", Map.of("type", "integer", "description", "场次 id"),
                        "startTime", Map.of("type", "string", "description", "开演时间 yyyy-MM-dd HH:mm"),
                        "endTime", Map.of("type", "string", "description", "结束时间 yyyy-MM-dd HH:mm")
                ), List.of("sessionId", "startTime", "endTime")),
                args -> {
                    long id = reqLong(args, "sessionId");
                    var start = parseDateTime(reqStr(args, "startTime"));
                    var end = parseDateTime(reqStr(args, "endTime"));
                    if (!end.isAfter(start)) throw new IllegalArgumentException("结束时间必须晚于开演时间");
                    LiveSession s = sessionRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("场次 id=" + id + " 不存在"));
                    s.setStartTime(start);
                    s.setEndTime(end);
                    sessionRepository.save(s);
                    return "场次「%s」时间已改为 %s ~ %s".formatted(s.getName(), start, end);
                }));

        validate();   // 注册完立刻自检（单方法内调用，避免 @PostConstruct 顺序不确定）
    }

    /** 启动自检：坏的工具定义应该在启动时就炸，而不是等 LLM 调用时才发现 */
    void validate() {
        if (tools.size() != 15) throw new IllegalStateException("工具数量应为 15，实际 " + tools.size());
        for (AgentTool t : tools.values()) {
            if (t.parameters() == null || !"object".equals(t.parameters().get("type"))) {
                throw new IllegalStateException("工具 " + t.name() + " 的 parameters 必须是 object schema");
            }
            if (t.isWrite() && (t.description() == null || t.description().isBlank())) {
                throw new IllegalStateException("写工具 " + t.name() + " 必须有说明");
            }
        }
    }

    public AgentTool get(String name) {
        return name == null ? null : tools.get(name);
    }

    public List<Map<String, Object>> schemas() {
        return tools.values().stream().map(AgentTool::toOpenAiSchema).toList();
    }

    /**
     * 按身份取工具清单（小灯 v2 的身份闸门，docs/13 §3.3）：
     * 普通用户/游客只能拿到只读工具，写工具根本不出现在清单里——模型无从调用。
     */
    public List<Map<String, Object>> schemas(boolean includeWrite) {
        return tools.values().stream()
                .filter(t -> includeWrite || !t.isWrite())
                .map(AgentTool::toOpenAiSchema)
                .toList();
    }

    public int size() {
        return tools.size();
    }

    private void register(AgentTool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalStateException("工具名重复：" + tool.name());
        }
        tools.put(tool.name(), tool);
    }

    // ---------------- 参数工具（防御 LLM 幻觉，docs/10 §4.4） ----------------

    private static Map<String, Object> schema(Map<String, Object> props, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", props);
        if (!required.isEmpty()) s.put("required", required);
        return s;
    }

    private static long reqLong(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v == null) throw new IllegalArgumentException("缺少必填参数 " + key);
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 必须是整数");
        }
    }

    private static long optLong(Map<String, Object> args, String key, long def) {
        Object v = args == null ? null : args.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String reqStr(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v == null || String.valueOf(v).isBlank()) throw new IllegalArgumentException("缺少必填参数 " + key);
        return String.valueOf(v);
    }

    private static String optStr(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 解析 yyyy-MM-dd HH:mm / yyyy-MM-ddTHH:mm，容忍"2026-11-14 18:00:00" */
    private static java.time.LocalDateTime parseDateTime(String raw) {
        String v = raw.trim().replace('T', ' ');
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String p : patterns) {
            try {
                return java.time.LocalDateTime.parse(v,
                        java.time.format.DateTimeFormatter.ofPattern(p));
            } catch (Exception ignored) {
                // 试下一个格式
            }
        }
        throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm，收到：" + raw);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Map<Short, String> bandNames() {
        Map<Short, String> m = new HashMap<>();
        for (Band b : bandRepository.findAll()) m.put(b.getId(), b.getName());
        return m;
    }
}
