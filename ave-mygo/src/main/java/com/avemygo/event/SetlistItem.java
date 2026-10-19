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

/**
 * 演出曲表实体，对应表 setlist_item（docs/05 §3.12）。
 * 每行 = 某场次演出中的一首曲（含顺序与安可标记），曲目详情逻辑 FK → song。
 */
@Entity
@Table(name = "setlist_item")
@Getter
@Setter
@NoArgsConstructor
public class SetlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 逻辑 FK → live_session.id */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 逻辑 FK → song.id */
    @Column(name = "song_id", nullable = false)
    private Long songId;

    /** 演出顺序（1 起） */
    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    /** 是否安可曲 */
    @Column(name = "is_encore", nullable = false)
    private Byte isEncore = (byte) 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
