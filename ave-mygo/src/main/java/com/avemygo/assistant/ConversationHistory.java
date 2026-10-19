package com.avemygo.assistant;

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
 * AI 助手对话历史实体，对应表 conversation_history（docs/05 §3.13）。
 * user_id 可空（游客可问）；session_id 串联多轮。
 */
@Entity
@Table(name = "conversation_history")
@Getter
@Setter
@NoArgsConstructor
public class ConversationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 逻辑 FK → user.id（游客为 null） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** USER / ASSISTANT */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
