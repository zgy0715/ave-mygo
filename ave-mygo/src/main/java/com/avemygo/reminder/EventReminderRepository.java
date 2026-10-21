package com.avemygo.reminder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 提醒订阅数据访问。 */
public interface EventReminderRepository extends JpaRepository<EventReminder, Long> {

    List<EventReminder> findByUserIdOrderByIdDesc(Long userId);

    Optional<EventReminder> findByUserIdAndEventId(Long userId, Long eventId);

    /** 定时任务：把"已到开售时间但还没提醒"的订阅一次性置为已提醒 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE EventReminder r SET r.notified = 1, r.notifiedAt = :now
            WHERE r.notified = 0
              AND EXISTS (SELECT 1 FROM LiveSession s
                          WHERE s.eventId = r.eventId AND s.saleStartTime <= :now)
            """)
    int markDueNotified(@Param("now") LocalDateTime now);
}
