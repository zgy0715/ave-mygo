package com.avemygo.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 演出曲数据访问：按曲目顺序返回某场次的歌单 */
public interface SetlistItemRepository extends JpaRepository<SetlistItem, Long> {

    List<SetlistItem> findBySessionIdOrderByOrderNoAsc(Long sessionId);
}
