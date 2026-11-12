package com.avemygo.assistant.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.avemygo.admin.AdminEventService;
import com.avemygo.admin.dto.AdminEventDtos.EventUpsert;
import com.avemygo.admin.dto.AdminEventDtos.SessionUpsert;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 通过真实工具与测试库验证角色可见性，模型仅原样转述工具返回的内容。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssistantVisibilityTest {

    @Autowired private AssistantStreamService assistant;
    @Autowired private AdminEventService admin;
    @MockitoBean private StreamLlm llm;

    private Long draftId;
    private final String draftTitle = "[TEST] 尚未公开的演出";
    private final String draftSession = "[TEST] 尚未公开的场次";

    @BeforeEach
    void setUp() {
        draftId = admin.create(new EventUpsert(draftTitle, (short) 1, null, null,
                "秘密场馆", "DRAFT")).id();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        admin.createSession(draftId, new SessionUpsert(draftSession, start,
                start.plusHours(2), start.minusDays(1), "NOT_OPEN"));
        admin.create(new EventUpsert("[TEST] 可以公开的演出", (short) 1, null, null,
                "公开场馆", "PUBLISHED"));
        admin.create(new EventUpsert("[TEST] 已结束的演出", (short) 1, null, null,
                "历史场馆", "ENDED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"list_events", "get_event", "list_sessions"})
    void publicAssistantCannotReadDrafts(String tool) throws Exception {
        String reply = replyFromTool(tool, false);
        assertThat(reply).doesNotContain(draftTitle, draftSession, "秘密场馆");
        if ("list_events".equals(tool)) {
            assertThat(reply).contains("可以公开的演出", "已结束的演出");
        } else {
            assertThat(reply).contains("不存在");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"list_events", "get_event", "list_sessions"})
    void adminAssistantCanStillReadDrafts(String tool) throws Exception {
        String reply = replyFromTool(tool, true);
        assertThat(reply).contains("list_sessions".equals(tool) ? draftSession : draftTitle);
    }

    private String replyFromTool(String tool, boolean isAdmin) throws Exception {
        String args = "list_events".equals(tool) ? "{}" : "{\"eventId\":" + draftId + "}";
        when(llm.stream(anyList(), anyList(), any()))
                .thenReturn(new StreamLlm.Turn("", List.of(new StreamLlm.ToolCall("c1", tool, args))))
                .thenAnswer(invocation -> {
                    List<Map<String, Object>> messages = invocation.getArgument(0);
                    String result = messages.stream().filter(message -> "tool".equals(message.get("role")))
                            .map(message -> String.valueOf(message.get("content"))).findFirst().orElseThrow();
                    StreamLlm.Listener listener = invocation.getArgument(2);
                    listener.onContent(result);
                    return new StreamLlm.Turn(result, List.of());
                });
        StringBuilder text = new StringBuilder();
        assistant.chat("查询演出资料", null, null, isAdmin ? 1L : null, isAdmin, (type, data) -> {
            if ("delta".equals(type)) text.append(data.get("text"));
        });
        return text.toString();
    }
}
