package com.avemygo.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 场次数据访问。 */
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    /** 演出详情页：按开演时间正序列出场次 */
    List<LiveSession> findByEventIdOrderByStartTimeAsc(Long eventId);
}
