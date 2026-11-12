package com.avemygo.event;

import java.math.BigDecimal;
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

    /** 管理端：场次下全部票档（含停售） */
    List<TicketType> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 批量取多场次的票档（列表页防 N+1） */
    List<TicketType> findBySessionIdIn(Collection<Long> sessionIds);

    /**
     * 管理员只更新票档配置，不写 soldCount。库存下限与编辑在同一条 UPDATE 中校验，
     * 避免整行保存把并发下单/退款后的已售数覆盖成旧快照。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketType t
            SET t.name = COALESCE(:name, t.name),
                t.price = COALESCE(:price, t.price),
                t.totalStock = COALESCE(:totalStock, t.totalStock),
                t.maxPerUser = COALESCE(:maxPerUser, t.maxPerUser),
                t.status = COALESCE(:status, t.status),
                t.updatedAt = CURRENT_TIMESTAMP
            WHERE t.id = :id
              AND (:totalStock IS NULL OR :totalStock >= t.soldCount)
            """)
    int updateDetails(@Param("id") Long id, @Param("name") String name,
                      @Param("price") BigDecimal price, @Param("totalStock") Integer totalStock,
                      @Param("maxPerUser") Byte maxPerUser, @Param("status") Byte status);

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

    /** 数据看板：各场次库存/已售汇总（上座率分子分母） */
    @Query("""
            SELECT s.id, s.name, e.title, s.startTime,
                   COALESCE(SUM(t.totalStock), 0), COALESCE(SUM(t.soldCount), 0)
            FROM LiveSession s
            JOIN LiveEvent e ON e.id = s.eventId
            JOIN TicketType t ON t.sessionId = s.id
            WHERE t.status = 1
            GROUP BY s.id, s.name, e.title, s.startTime
            ORDER BY s.startTime DESC
            """)
    java.util.List<Object[]> sessionOccupancy();

    /** 库存回滚（取消/退款/超时）： sold_count 永不为负 */
    @Modifying
    @Query("""
            UPDATE TicketType t
            SET t.soldCount = t.soldCount - :qty
            WHERE t.id = :id AND t.soldCount >= :qty
            """)
    int decreaseSold(@Param("id") Long id, @Param("qty") int qty);

    /** 场次级联删除：软删该场次全部票档 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TicketType t SET t.isDeleted = 1 WHERE t.sessionId = :sessionId")
    int softDeleteBySessionId(@Param("sessionId") Long sessionId);
}
