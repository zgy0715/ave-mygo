package com.avemygo.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 演出曲数据访问：按曲目顺序返回某场次的歌单 */
public interface SetlistItemRepository extends JpaRepository<SetlistItem, Long> {

    List<SetlistItem> findBySessionIdOrderByOrderNoAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    /**
     * 全量替换时先清空该场次曲目（管理端 PUT 语义）。
     * 必须是显式 @Query DELETE：表上有唯一键 (session_id, order_no)，
     * 若用派生 delete（先查后逐条删）会排队到 flush，新行先插入 → 唯一键冲突。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SetlistItem i WHERE i.sessionId = :sessionId")
    int deleteBySessionIdQuery(@Param("sessionId") Long sessionId);

    /** 场次级联删除：软删该场次全部曲目单 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SetlistItem i SET i.isDeleted = 1 WHERE i.sessionId = :sessionId")
    int softDeleteBySessionId(@Param("sessionId") Long sessionId);
}
