package com.avemygo.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 演出数据访问。DRAFT 永远不出现在前台查询结果里。 */
public interface EventRepository extends JpaRepository<LiveEvent, Long> {

    /** 前台演出列表：状态/乐队可选筛选 + 分页 */
    @Query("""
            SELECT e FROM LiveEvent e
            WHERE e.status <> 'DRAFT'
              AND (:status IS NULL OR e.status = :status)
              AND (:bandId IS NULL OR e.bandId = :bandId)
            """)
    Page<LiveEvent> search(@Param("status") String status,
                           @Param("bandId") Short bandId,
                           Pageable pageable);

    /** 管理端列表：包含 DRAFT/ENDED，可按状态与乐队筛选 */
    @Query("""
            SELECT e FROM LiveEvent e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:bandId IS NULL OR e.bandId = :bandId)
            """)
    Page<LiveEvent> searchAdmin(@Param("status") String status,
                                @Param("bandId") Short bandId,
                                Pageable pageable);

    /** 状态机：已发布演出若所有场次都已散场 → ENDED */
    @Modifying
    @Query("""
            UPDATE LiveEvent e SET e.status = 'ENDED'
            WHERE e.status = 'PUBLISHED'
              AND EXISTS (SELECT 1 FROM LiveSession s WHERE s.eventId = e.id)
              AND NOT EXISTS (SELECT 1 FROM LiveSession s2 WHERE s2.eventId = e.id AND s2.endTime > :now)
            """)
    int finishEndedEvents(@Param("now") LocalDateTime now);
}
