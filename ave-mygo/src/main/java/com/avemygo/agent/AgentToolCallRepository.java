package com.avemygo.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Agent 审计数据访问。 */
public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {

    Optional<AgentToolCall> findByToken(String token);

    List<AgentToolCall> findByConversationIdOrderByIdAsc(String conversationId);
}
