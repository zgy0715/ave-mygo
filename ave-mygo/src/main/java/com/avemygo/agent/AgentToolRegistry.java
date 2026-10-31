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

        validate();   // 注册完立刻自检（单方法内调用，避免 @PostConstruct 顺序不确定）
    }

    /** 启动自检：坏的工具定义应该在启动时就炸，而不是等 LLM 调用时才发现 */
    void validate() {
        if (tools.size() != 8) throw new IllegalStateException("工具数量应为 8，实际 " + tools.size());
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Map<Short, String> bandNames() {
        Map<Short, String> m = new HashMap<>();
        for (Band b : bandRepository.findAll()) m.put(b.getId(), b.getName());
        return m;
    }
}
