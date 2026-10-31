package com.avemygo.agent;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Agent 会话消息数据访问。 */
public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    /** 取会话最近 N 条（按 id 倒序取，调用方再反转为时间正序） */
    List<AgentMessage> findByConversationIdOrderByIdDesc(String conversationId, Pageable pageable);
}
