package com.avemygo.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Agent 审计数据访问。 */
public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {

    Optional<AgentToolCall> findByToken(String token);

    /**
     * 抢占：PENDING → EXECUTING。
     * 两个确认请求同时进来时，只有一条 UPDATE 能命中（status='PENDING' 只成立一次），
     * 抢不到的那个直接返回 40902，不会重复执行写操作。
     * @return 1=抢到，0=已被其他请求处理
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentToolCall c SET c.status = 'EXECUTING' "
            + "WHERE c.token = :token AND c.status = 'PENDING'")
    int claim(@Param("token") String token);

    /** 取消：PENDING → REJECTED（条件更新，避免与抢占互相覆盖） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentToolCall c SET c.status = 'REJECTED' "
            + "WHERE c.token = :token AND c.status = 'PENDING'")
    int markRejected(@Param("token") String token);

    /** 执行完毕：EXECUTING → EXECUTED / FAILED（只更新自己抢到的那一行） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentToolCall c SET c.status = :status, c.result = :result "
            + "WHERE c.token = :token AND c.status = 'EXECUTING'")
    int finish(@Param("token") String token, @Param("status") String status,
               @Param("result") String result);

    /** 过期：PENDING → EXPIRED（条件更新，避免把已执行的改成过期） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentToolCall c SET c.status = 'EXPIRED' "
            + "WHERE c.token = :token AND c.status = 'PENDING'")
    int markExpired(@Param("token") String token);

    List<AgentToolCall> findByConversationIdOrderByIdAsc(String conversationId);
}
