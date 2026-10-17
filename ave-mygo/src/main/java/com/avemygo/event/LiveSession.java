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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 演出场次实体，对应表 live_session（docs/05 §3.7）。
 * 状态机（定时任务驱动）：NOT_OPEN --到达开售时间--> ONSALE --到达散场时间--> ENDED
 * SOLD_OUT（售罄）不落库：余票=0 的派生状态，查询时计算。
 */
@Entity
@Table(name = "live_session")
@Getter
@Setter
@NoArgsConstructor
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 逻辑 FK → live_event.id */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** 场次名，如 昼场 / 夜场 */
    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 预计散场时间（定时任务据此翻转 ENDED） */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** 开售时间（定时任务据此翻转 ONSALE） */
    @Column(name = "sale_start_time", nullable = false)
    private LocalDateTime saleStartTime;

    /** NOT_OPEN / ONSALE / ENDED */
    @Column(nullable = false, length = 20)
    private String status = "NOT_OPEN";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Byte isDeleted = (byte) 0;
}
