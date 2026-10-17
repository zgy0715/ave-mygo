package com.avemygo.event;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 票档数据访问。库存扣减用原子 UPDATE（防超卖核心），查询走派生方法。 */
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    /** 场次下的启用票档（购票面板用） */
    List<TicketType> findBySessionIdAndStatusOrderByIdAsc(Long sessionId, Byte status);

    /** 批量取多场次的票档（列表页防 N+1） */
    List<TicketType> findBySessionIdIn(Collection<Long> sessionIds);

    /**
     * 防超卖核心：单条原子 UPDATE，检查与扣减一次完成。
     * WHERE 不满足时影响行数为 0 —— 这就是"库存不足"信号，绝无竞态窗口。
     */
    @Modifying
    @Query("""
            UPDATE TicketType t
            SET t.soldCount = t.soldCount + :qty
            WHERE t.id = :id AND t.status = 1
              AND t.soldCount + :qty <= t.totalStock
            """)
    int increaseSold(@Param("id") Long id, @Param("qty") int qty);

    /** 库存回滚（取消/退款/超时）： sold_count 永不为负 */
    @Modifying
    @Query("""
            UPDATE TicketType t
            SET t.soldCount = t.soldCount - :qty
            WHERE t.id = :id AND t.soldCount >= :qty
            """)
    int decreaseSold(@Param("id") Long id, @Param("qty") int qty);
}
