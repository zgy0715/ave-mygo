package com.avemygo.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 超时任务扫描：待支付且已过支付截止时间（限量，避免一次捞全表） */
    List<TicketOrder> findTop200ByStatusAndPayDeadlineBeforeOrderByIdAsc(String status,
                                                                        LocalDateTime deadline);

    // ---------- 原子状态转换（并发安全：谁 UPDATE 成功谁生效，失败方按影响行数判定） ----------

    /**
     * 支付：PENDING_PAYMENT → PAID，且必须未过支付截止时间。
     * 条件写在 UPDATE 里，杜绝"两个请求都读到 PENDING 各自保存"。
     * @return 1=本次转换成功，0=状态已被他人改变或已超时
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketOrder o SET o.status = 'PAID', o.paidAt = :now
            WHERE o.orderNo = :orderNo AND o.userId = :userId
              AND o.status = 'PENDING_PAYMENT' AND o.payDeadline > :now
            """)
    int markPaid(@Param("orderNo") String orderNo, @Param("userId") Long userId,
                 @Param("now") LocalDateTime now);

    /** 用户取消：PENDING_PAYMENT → CANCELLED。@return 1=成功，0=状态已变 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketOrder o SET o.status = 'CANCELLED', o.cancelledAt = :now
            WHERE o.orderNo = :orderNo AND o.userId = :userId AND o.status = 'PENDING_PAYMENT'
            """)
    int markCancelled(@Param("orderNo") String orderNo, @Param("userId") Long userId,
                      @Param("now") LocalDateTime now);

    /** 超时取消：只转换仍处于待支付且确实过期的订单。@return 1=成功，0=已被支付/取消 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketOrder o SET o.status = 'EXPIRED', o.cancelledAt = :now
            WHERE o.id = :id AND o.status = 'PENDING_PAYMENT' AND o.payDeadline <= :now
            """)
    int markExpired(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 管理端退款：PAID → REFUNDED。@return 1=成功，0=状态已变（如已退款） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketOrder o SET o.status = 'REFUNDED', o.refundedAt = :now, o.refundReason = :reason
            WHERE o.orderNo = :orderNo AND o.status = 'PAID'
            """)
    int markRefunded(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now,
                     @Param("reason") String reason);

    /** 删除前置校验：演出/场次下是否存在未完成订单（待支付或已支付） */
    boolean existsByEventIdAndStatusIn(Long eventId, java.util.Collection<String> statuses);

    boolean existsBySessionIdAndStatusIn(Long sessionId, java.util.Collection<String> statuses);
}
