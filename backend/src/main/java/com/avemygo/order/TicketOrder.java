package com.avemygo.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 购票订单实体，对应表 ticket_order（docs/05 §3.9）—— 订单状态机核心。
 * 无 is_deleted：订单只允许状态流转，不允许删除（审计要求）。
 * unit_price/total_amount 是下单时的价格快照，票档后续改价不影响历史订单。
 */
@Entity
@Table(name = "ticket_order")
@Getter
@Setter
@NoArgsConstructor
public class TicketOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务单号：BL + yyyyMMdd + 10位随机，唯一索引兜底 */
    @Column(name = "order_no", nullable = false, length = 32, unique = true)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 冗余字段：从场次反查演出代价高，订单列表按演出筛选高频 */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    /** TINYINT UNSIGNED → Byte；业务上限 10（docs/05 §3.9） */
    @Column(nullable = false)
    private Byte quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** PENDING_PAYMENT / PAID / CANCELLED / EXPIRED / REFUNDED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING_PAYMENT";

    /** 支付截止时间（15 分钟），超时任务扫描依据 */
    @Column(name = "pay_deadline", nullable = false)
    private LocalDateTime payDeadline;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_reason", length = 200)
    private String refundReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
