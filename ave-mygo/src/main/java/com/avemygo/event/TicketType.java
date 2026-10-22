package com.avemygo.event;

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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 票档实体，对应表 ticket_type（docs/05 §3.8）—— 防超卖核心表。
 * 铁律：金额一律 BigDecimal；available = total_stock - sold_count 派生不落库；
 * CHECK 约束 chk_stock 是数据库层的最后一道防线。
 */
@Entity
@Table(name = "ticket_type")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@NoArgsConstructor
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 逻辑 FK → live_session.id */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 票档名，如 S席 / 普通席 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 单价（元）—— 金钱字段必须 BigDecimal */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "sold_count", nullable = false)
    private Integer soldCount = 0;

    /** 单人限购数 */
    @Column(name = "max_per_user", nullable = false)
    private Byte maxPerUser = (byte) 4;

    /** 1 启用 / 0 停用（停用后不可下单，已出订单不受影响） */
    @Column(nullable = false)
    private Byte status = (byte) 1;

    /** 软删标记：0 正常 / 1 已删（场次级联删除时置 1） */
    @Column(name = "is_deleted", nullable = false)
    private Byte isDeleted = (byte) 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 派生字段：余票（不对应数据库列，查询后由 Service 填充） */
    public int availableStock() {
        return totalStock - soldCount;
    }
}
