package com.avemygo.order;

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
 * 限购配额（V53）：一行 = 某用户在某场次已占用的张数。
 *
 * 存在的意义是让「检查限购 + 占用名额」变成一条原子 UPDATE，
 * 而不是先 SELECT SUM 再 INSERT 订单（并发下会突破 max_per_user）。
 * 生命周期与订单状态一致：
 *   下单成功 → qty += 本笔数量
 *   取消 / 超时 / 退款 → qty -= 本笔数量
 */
@Entity
@Table(name = "user_session_quota")
@Getter
@Setter
@NoArgsConstructor
public class UserSessionQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Integer qty = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
