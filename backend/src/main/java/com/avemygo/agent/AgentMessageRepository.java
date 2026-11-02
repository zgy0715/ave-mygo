package com.avemygo.agent;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Agent 会话消息数据访问。 */
public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    /**
     * 取「某用户的某会话」最近 N 条。
     * 必须带 userId：conversationId 由前端生成，只按它查会把别人的历史读进当前对话
     * （2026-11-02 审阅项 1）。
     */
    List<AgentMessage> findByUserIdAndConversationIdOrderByIdDesc(
            Long userId, String conversationId, Pageable pageable);
}
