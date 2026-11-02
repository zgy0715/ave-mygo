package com.avemygo.assistant.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.avemygo.auth.JwtService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 小灯 v2 编排测试（docs/13）。
 * 用脚本化假 StreamLlm 驱动，覆盖：身份闸门、真流式事件、工具链路、写操作门控、
 * 寒暄快速通道、同工具两错即停。
 */
@SpringBootTest
@ActiveProfiles("test")
class AssistantStreamServiceTest {

    @Autowired private AssistantStreamService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    @MockitoBean private StreamLlm streamLlm;

    /** 收集事件的假 Sink */
    private final List<Map<String, Object>> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        events.clear();
        jdbc.update("DELETE FROM agent_messages");
        jdbc.update("DELETE FROM agent_tool_calls");
    }

    private AssistantStreamService.Sink sink() {
        return (type, data) -> {
            Map<String, Object> e = new java.util.LinkedHashMap<>(data == null ? Map.of() : data);
            e.put("__type", type);
            events.add(e);
        };
    }

    private long countOfType(String type) {
        return events.stream().filter(e -> type.equals(e.get("__type"))).count();
    }

    private String textOfDeltas() {
        StringBuilder sb = new StringBuilder();
        events.stream().filter(e -> "delta".equals(e.get("__type")))
                .forEach(e -> sb.append(e.get("text")));
        return sb.toString();
    }

    @Test
    @DisplayName("V01 游客：工具清单只有只读（结构上拿不到写工具）")
    void v01_guestReadOnlyTools() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any())).thenReturn(
                new StreamLlm.Turn("你好呀。", List.of()));

        service.chat("有什么演出", null, null, 1L, false, sink());

        List<List<Map<String, Object>>> captured = new ArrayList<>();
        org.mockito.Mockito.verify(streamLlm).stream(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.argThat(tools -> {
                    captured.add((List<Map<String, Object>>) tools);
                    return true;
                }),
                any());

        List<String> names = captured.get(0).stream()
                .map(t -> String.valueOf(((Map<?, ?>) t.get("function")).get("name")))
                .toList();
        assertThat(names).contains("search_songs", "list_events", "list_members");
        assertThat(names).doesNotContain("update_song_mv", "delete_event", "set_song_status");
    }

    @Test
    @DisplayName("V02 管理员：工具清单包含写工具")
    void v02_adminHasWriteTools() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any())).thenReturn(
                new StreamLlm.Turn("好。", List.of()));

        service.chat("有什么演出", null, null, 1L, true, sink());

        List<List<Map<String, Object>>> captured = new ArrayList<>();
        org.mockito.Mockito.verify(streamLlm).stream(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.argThat(tools -> {
                    captured.add((List<Map<String, Object>>) tools);
                    return true;
                }),
                any());
        List<String> names = captured.get(0).stream()
                .map(t -> String.valueOf(((Map<?, ?>) t.get("function")).get("name")))
                .toList();
        assertThat(names).contains("update_song_mv", "delete_event", "update_session_time");
        assertThat(names).hasSize(15);
    }

    @Test
    @DisplayName("V03 真流式：内容分片逐段推送 + done 收尾")
    void v03_streamDeltas() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any())).thenAnswer(inv -> {
            StreamLlm.Listener l = inv.getArgument(2);
            l.onThinking();
            l.onContent("《春日影》");
            l.onContent("收录于《迷跡波》。");
            return new StreamLlm.Turn("《春日影》收录于《迷跡波》。", List.of());
        });

        service.chat("春日影是哪张专辑的", null, null, null, false, sink());

        assertThat(countOfType("status")).isEqualTo(1);
        assertThat(countOfType("delta")).isEqualTo(2);
        assertThat(textOfDeltas()).isEqualTo("《春日影》收录于《迷跡波》。");
        assertThat(countOfType("done")).isEqualTo(1);
    }

    @Test
    @DisplayName("V04 工具链路：查询 → 回填 → 第二轮给出结论")
    void v04_toolLoop() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any()))
                .thenReturn(new StreamLlm.Turn("", List.of(
                                new StreamLlm.ToolCall("c1", "list_events", "{}"))))
                .thenAnswer(inv -> {
                    StreamLlm.Listener l = inv.getArgument(2);
                    l.onThinking();
                    l.onContent("站里现在有 6 场演出。");
                    return new StreamLlm.Turn("站里现在有 6 场演出。", List.of());
                });

        service.chat("有哪些演出", null, null, 1L, true, sink());

        List<String> toolStatuses = events.stream()
                .filter(e -> "tool".equals(e.get("__type")))
                .map(e -> e.get("name") + ":" + e.get("status"))
                .toList();
        assertThat(toolStatuses).containsExactly("list_events:RUNNING", "list_events:OK");
        assertThat(textOfDeltas()).contains("6 场演出");
        assertThat(countOfType("done")).isEqualTo(1);
        // 审计落库
        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_tool_calls WHERE tool_name='list_events'", Integer.class);
        assertThat(audits).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("V05 写操作：不执行，只推 confirm（带人类可读标题）")
    void v05_writeNeedsConfirm() throws Exception {
        String mvBefore = jdbc.queryForObject("SELECT mv_url FROM song WHERE id=1", String.class);
        when(streamLlm.stream(anyList(), anyList(), any())).thenReturn(
                new StreamLlm.Turn("", List.of(new StreamLlm.ToolCall("c1", "update_song_mv",
                        "{\"songId\":1,\"mvUrl\":\"https://example.com/v2-test\"}"))));

        service.chat("把这首歌的 MV 换成 https://example.com/v2-test", null, null, 1L, true, sink());

        Map<String, Object> confirm = events.stream()
                .filter(e -> "confirm".equals(e.get("__type"))).findFirst().orElseThrow();
        assertThat(confirm.get("token")).isNotNull();
        assertThat(confirm.get("toolName")).isEqualTo("update_song_mv");
        assertThat(String.valueOf(confirm.get("title"))).contains("《");   // 带歌名，不是裸 id
        // 门控：数据没动
        String mvAfter = jdbc.queryForObject("SELECT mv_url FROM song WHERE id=1", String.class);
        assertThat(mvAfter).isEqualTo(mvBefore);
    }

    @Test
    @DisplayName("V06 非管理员即使模型硬调写工具，也不给确认卡片")
    void v06_guestCannotWrite() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any())).thenReturn(
                new StreamLlm.Turn("", List.of(new StreamLlm.ToolCall("c1", "delete_event",
                        "{\"eventId\":1}"))));

        service.chat("删掉第一个演出", null, null, 999L, false, sink());

        assertThat(countOfType("confirm")).isZero();
        assertThat(events.stream()
                .filter(e -> "tool".equals(e.get("__type")))
                .map(e -> e.get("status")))
                .contains("FAIL");
    }

    @Test
    @DisplayName("V07 寒暄快速通道：不挂工具，直接回")
    void v07_greetingFastPath() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any())).thenReturn(
                new StreamLlm.Turn("晚上好，我是小灯。", List.of()));

        service.chat("你好", null, null, null, true, sink());

        List<List<Map<String, Object>>> captured = new ArrayList<>();
        org.mockito.Mockito.verify(streamLlm).stream(anyList(),
                org.mockito.ArgumentMatchers.argThat(tools -> {
                    captured.add((List<Map<String, Object>>) tools);
                    return true;
                }), any());
        assertThat(captured.get(0)).isEmpty();
        assertThat(textOfDeltas()).isEmpty();     // 假 LLM 没触发 onContent，靠 done 判定
        assertThat(countOfType("done")).isEqualTo(1);
    }

    @Test
    @DisplayName("V11 成员类问题预取事实：system 里注入真实 CV，避免模型凭记忆编造")
    void v11_prefetchMemberFacts() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any()))
                .thenReturn(new StreamLlm.Turn("五位。", List.of()));

        service.chat("MyGO有哪些成员？", null, "anon", 1L, false, sink());

        List<List<Map<String, Object>>> calls = new ArrayList<>();
        org.mockito.Mockito.verify(streamLlm).stream(
                org.mockito.ArgumentMatchers.argThat(msgs -> {
                    calls.add((List<Map<String, Object>>) msgs);
                    return true;
                }), anyList(), any());

        String system = String.valueOf(calls.get(0).get(0).get("content"));
        assertThat(system).contains("站内资料 · 成员");
        assertThat(system).contains("羊宮妃那");          // 高松燈的真实 CV（库里的事实）
        assertThat(system).contains("禁止使用训练记忆");
        // 预取也应推给前端一条工具事件
        assertThat(events.stream()
                .filter(e -> "tool".equals(e.get("__type")))
                .map(e -> e.get("name") + ":" + e.get("status")))
                .contains("list_members:RUNNING", "list_members:OK");
    }

    @Test
    @DisplayName("V12 游客不落库、也不读历史（没有身份就无法隔离）")
    void v12_guestNoHistory() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any()))
                .thenReturn(new StreamLlm.Turn("好。", List.of()));

        service.chat("有什么演出", "guest-conv", null, null, false, sink());

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_messages WHERE conversation_id = 'guest-conv'",
                Integer.class);
        assertThat(rows).isZero();

        // 第二次同样的 conversationId 也不应该带出历史（否则不同游客会串话）
        List<List<Map<String, Object>>> calls = new ArrayList<>();
        when(streamLlm.stream(anyList(), anyList(), any())).thenAnswer(inv -> {
            calls.add((List<Map<String, Object>>) inv.getArgument(0));
            return new StreamLlm.Turn("嗯。", List.of());
        });
        service.chat("再说一次", "guest-conv", null, null, false, sink());
        assertThat(calls.get(0)).hasSize(2);   // 只有 system + 本次 user
    }

    @Test
    @DisplayName("V10 人设：persona 不同则 system 提示词不同（修复 ANON 说客服话）")
    void v10_personaInjected() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any()))
                .thenReturn(new StreamLlm.Turn("嗯。", List.of()));

        service.chat("你好", null, "anon", 1L, false, sink());
        service.chat("你好", null, "mutsumi", 1L, false, sink());
        service.chat("你好", null, "不存在的角色", 1L, false, sink());

        List<List<Map<String, Object>>> calls = new ArrayList<>();
        org.mockito.Mockito.verify(streamLlm, org.mockito.Mockito.times(3))
                .stream(org.mockito.ArgumentMatchers.argThat(msgs -> {
                    calls.add((List<Map<String, Object>>) msgs);
                    return true;
                }), anyList(), any());

        String anonSystem = String.valueOf(calls.get(0).get(0).get("content"));
        String mutsumiSystem = String.valueOf(calls.get(1).get(0).get("content"));
        String fallbackSystem = String.valueOf(calls.get(2).get(0).get("content"));

        assertThat(anonSystem).contains("千早愛音").contains("那个那个").contains("角色：愛音");
        assertThat(anonSystem).contains("口癖·有节制");
        assertThat(mutsumiSystem).contains("若葉睦").contains("要点式").contains("口癖·有节制");
        // 未知 key 回退小灯
        assertThat(fallbackSystem).contains("高松燈").contains("角色：小灯");
        // 三条都必须带运营规则，不能因为人设丢能力
        assertThat(anonSystem).contains("必须先调用工具");
        assertThat(mutsumiSystem).contains("必须先调用工具");
    }

    @Test
    @DisplayName("V09 markdown 兜底：模型输出 **id=19** 时，流里不出现星号")
    void v09_markdownStripped() throws Exception {
        when(streamLlm.stream(anyList(), anyList(), any())).thenAnswer(inv -> {
            StreamLlm.Listener l = inv.getArgument(2);
            l.onContent("1. **id=19** 《MyGO!!!!! 9th》\n## 列表\n用 `search_songs` 查");
            return new StreamLlm.Turn("1. **id=19** 《MyGO!!!!! 9th》\n## 列表\n用 `search_songs` 查", List.of());
        });

        service.chat("列一下", "md-conv", null, 1L, false, sink());   // 登录用户才会落库

        String text = textOfDeltas();
        assertThat(text).doesNotContain("*").doesNotContain("`").doesNotContain("##");
        assertThat(text).contains("1. id=19").contains("search_songs");
        // 落库的历史同样干净（避免下一轮把 markdown 再喂回模型）
        String stored = jdbc.queryForObject(
                "SELECT content FROM agent_messages WHERE role='ASSISTANT' ORDER BY id DESC LIMIT 1", String.class);
        assertThat(stored).doesNotContain("**");
    }

    @Test
    @DisplayName("V08 同一工具连错两次：停下来问用户，不再无限重试")
    void v08_stopAfterTwoFails() throws Exception {
        // get_song 缺必填参数 → 每次都抛异常
        when(streamLlm.stream(anyList(), anyList(), any())).thenReturn(
                new StreamLlm.Turn("", List.of(new StreamLlm.ToolCall("c1", "get_song", "{}"))));

        service.chat("看看这首歌", null, null, 1L, true, sink());

        assertThat(textOfDeltas()).contains("连续失败了两次");
        assertThat(countOfType("done")).isEqualTo(1);
        // 只调用了两轮（第一轮 + 重试一轮），没有无限循环
        org.mockito.Mockito.verify(streamLlm, org.mockito.Mockito.times(2))
                .stream(anyList(), anyList(), any());
    }
}
