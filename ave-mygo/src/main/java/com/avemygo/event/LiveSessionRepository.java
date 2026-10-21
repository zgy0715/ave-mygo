package com.avemygo.event;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 场次数据访问。 */
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    /** 演出详情页：按开演时间正序列出场次 */
    List<LiveSession> findByEventIdOrderByStartTimeAsc(Long eventId);

    /** 批量取多场演出的场次（列表页防 N+1） */
    List<LiveSession> findByEventIdIn(Collection<Long> eventIds);

    /** 搜索用：批量取各演出「下一场」开演时间（已开演/已结束的不算） */
    @Query("""
            SELECT s.eventId, MIN(s.startTime) FROM LiveSession s
            WHERE s.eventId IN :eventIds AND s.startTime > :now
            GROUP BY s.eventId
            """)
    java.util.List<Object[]> nextStartByEvent(@Param("eventIds") java.util.Collection<Long> eventIds,
                                              @Param("now") LocalDateTime now);

    /** 管理端列表：演出下有几场 */
    long countByEventId(Long eventId);

    /**
     * 状态机（定时任务）：到开售时间且未散场的 NOT_OPEN 场次 → ONSALE。
     * 单条 UPDATE，影响行数即本次翻转数量；可重复执行（已 ONSALE 不会重复命中）。
     */
    @Modifying
    @Query("""
            UPDATE LiveSession s SET s.status = 'ONSALE'
            WHERE s.status = 'NOT_OPEN' AND s.saleStartTime <= :now AND s.endTime > :now
            """)
    int activateDueSessions(@Param("now") LocalDateTime now);

    /** 状态机：已过散场时间的未结束场次（NOT_OPEN/ONSALE）→ ENDED */
    @Modifying
    @Query("""
            UPDATE LiveSession s SET s.status = 'ENDED'
            WHERE s.status IN ('NOT_OPEN', 'ONSALE') AND s.endTime <= :now
            """)
    int finishEndedSessions(@Param("now") LocalDateTime now);
}
