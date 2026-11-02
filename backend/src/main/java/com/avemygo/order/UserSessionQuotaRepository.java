package com.avemygo.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 限购配额的原子操作。
 *
 * 核心思想：把「检查是否超限 + 占用名额」压成一条带条件的 UPDATE，
 * 用影响行数当结论——0 行 = 名额不够，1 行 = 抢占成功。
 * 这样并发下不需要任何 SELECT ... FOR UPDATE，也不会出现两个请求都判定"没超"。
 */
public interface UserSessionQuotaRepository extends JpaRepository<UserSessionQuota, Long> {

    /** 确保配额行存在（并发安全：INSERT IGNORE，谁先插谁生效） */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO user_session_quota (user_id, session_id, qty, created_at, updated_at)
            VALUES (:userId, :sessionId, 0, NOW(), NOW())
            """, nativeQuery = true)
    int ensureRow(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    /**
     * 占用名额：qty + 本笔 <= 限购上限 才更新。
     * @return 1=占用成功，0=会超出限购
     */
    @Modifying
    @Query(value = """
            UPDATE user_session_quota SET qty = qty + :qty
            WHERE user_id = :userId AND session_id = :sessionId AND qty + :qty <= :maxPerUser
            """, nativeQuery = true)
    int reserve(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                @Param("qty") int qty, @Param("maxPerUser") int maxPerUser);

    /** 释放名额（取消/超时/退款）。qty 不足时不动，避免负数 */
    @Modifying
    @Query(value = """
            UPDATE user_session_quota SET qty = qty - :qty
            WHERE user_id = :userId AND session_id = :sessionId AND qty >= :qty
            """, nativeQuery = true)
    int release(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                @Param("qty") int qty);

    /** 当前已占用张数（只读展示用） */
    @Query("SELECT COALESCE(q.qty, 0) FROM UserSessionQuota q "
            + "WHERE q.userId = :userId AND q.sessionId = :sessionId")
    Integer occupied(@Param("userId") Long userId, @Param("sessionId") Long sessionId);
}
