package com.avemygo.assistant;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 对话历史数据访问 */
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, Long> {

    /** 取某会话最近 N 条（时间正序），用于多轮上下文 */
    List<ConversationHistory> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);
}
