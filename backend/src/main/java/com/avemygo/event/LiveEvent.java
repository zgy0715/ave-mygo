package com.avemygo.event;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 演出实体，对应表 live_event（docs/05 §3.6）。
 * 状态机：DRAFT 草稿 → PUBLISHED 已发布 → ENDED/CANCELLED（仅管理员可见 DRAFT）。
 */
@Entity
@Table(name = "live_event")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@NoArgsConstructor
public class LiveEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 逻辑 FK → band.id */
    @Column(name = "band_id", nullable = false)
    private Short bandId;

    @Column(columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "poster_url")
    private String posterUrl;

    /** 场馆名 */
    @Column(nullable = false, length = 100)
    private String venue;

    /** DRAFT / PUBLISHED / ENDED / CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Byte isDeleted = (byte) 0;
}
