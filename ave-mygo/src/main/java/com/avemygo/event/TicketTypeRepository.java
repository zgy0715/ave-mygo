package com.avemygo.event;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 票档数据访问。下单扣减在 Service 用原子 UPDATE 实现（防超卖），不走这里。 */
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    /** 场次下的启用票档（购票面板用） */
    List<TicketType> findBySessionIdAndStatusOrderByIdAsc(Long sessionId, Byte status);

    /** 批量取多场次的票档（列表页防 N+1） */
    List<TicketType> findBySessionIdIn(Collection<Long> sessionIds);
}
