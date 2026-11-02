package com.avemygo.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Agent 会话消息（docs/10 §15）：只存人机对话文本，用于多轮上下文。
 * 工具调用的参数与结果在 agent_tool_calls（审计），不进本表，避免上下文被工具输出淹没。
 */
@Entity
@Table(name = "agent_messages")
@Getter
@Setter
@NoArgsConstructor
public class AgentMessage {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
