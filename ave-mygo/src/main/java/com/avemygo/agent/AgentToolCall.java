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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Agent 工具调用审计行（docs/10 §7）。
 * 一行 = 一次工具调用；WRITE 操作在确认前是 PENDING，确认后流转为 EXECUTED/REJECTED/FAILED/EXPIRED。
 * 只增不删：出问题可回溯「谁让 Agent 改了什么」。
 */
@Entity
@Table(name = "agent_tool_calls")
@Getter
@Setter
@NoArgsConstructor
public class AgentToolCall {

    public static final String PENDING = "PENDING";
    public static final String EXECUTED = "EXECUTED";
    public static final String REJECTED = "REJECTED";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(nullable = false, length = 8)
    private String risk;

    @Column(name = "arguments_json", nullable = false, columnDefinition = "TEXT")
    private String argumentsJson;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 36)
    private String token;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
