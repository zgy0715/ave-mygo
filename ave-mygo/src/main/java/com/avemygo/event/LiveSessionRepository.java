package com.avemygo.event;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 场次数据访问。 */
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    /** 演出详情页：按开演时间正序列出场次 */
    List<LiveSession> findByEventIdOrderByStartTimeAsc(Long eventId);

    /** 批量取多场演出的场次（列表页防 N+1） */
    List<LiveSession> findByEventIdIn(Collection<Long> eventIds);
}
