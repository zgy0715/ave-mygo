package com.avemygo.order;

import java.util.List;
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

    /** 我的订单列表（按创建时间倒序） */
    List<TicketOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
}
