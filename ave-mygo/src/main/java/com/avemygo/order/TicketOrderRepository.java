package com.avemygo.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 订单数据访问。 */
public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    /** 限购校验：同一用户同一场次"未取消"订单的购票数合计（含待支付/已支付） */
    @Query("""
            SELECT COALESCE(SUM(o.quantity), 0) FROM TicketOrder o
            WHERE o.userId = :userId AND o.sessionId = :sessionId
              AND o.status IN ('PENDING_PAYMENT', 'PAID')
            """)
    int sumActiveQuantity(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    /**
     * 管理端订单检索：状态 / 场次 / 关键词（订单号或用户名）三选任意组合 + 分页。
     * 用户名用 EXISTS 子查询匹配，避免关联查询把分页 count 搞乱。
     */
    @Query("""
            SELECT o FROM TicketOrder o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:eventId IS NULL OR o.eventId = :eventId)
              AND (:kw IS NULL
                   OR o.orderNo LIKE CONCAT('%', :kw, '%')
                   OR EXISTS (SELECT 1 FROM User u WHERE u.id = o.userId
                              AND u.username LIKE CONCAT('%', :kw, '%')))
            """)
    Page<TicketOrder> adminSearch(@Param("status") String status,
                                  @Param("eventId") Long eventId,
                                  @Param("kw") String kw,
                                  Pageable pageable);

    // ---------- 数据看板聚合（只统计 PAID，退款单不计入营收） ----------

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM TicketOrder o WHERE o.status = 'PAID'")
    java.math.BigDecimal totalRevenue();

    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM TicketOrder o WHERE o.status = 'PAID'")
    long totalTicketsSold();

    long countByStatus(String status);

    /** 近 N 天每日营收/订单数（按支付时间归集） */
    @Query("""
            SELECT FUNCTION('DATE', o.paidAt), COUNT(o), COALESCE(SUM(o.totalAmount), 0)
            FROM TicketOrder o
            WHERE o.status = 'PAID' AND o.paidAt >= :from
            GROUP BY FUNCTION('DATE', o.paidAt)
            """)
    java.util.List<Object[]> dailyPaidSince(@Param("from") LocalDateTime from);

    /** 我的订单列表（按创建时间倒序） */
    List<TicketOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TicketOrder> findByOrderNo(String orderNo);

    /** 超时任务扫描：待支付且已过支付截止时间 */
    List<TicketOrder> findByStatusAndPayDeadlineBefore(String status, LocalDateTime deadline);
}
