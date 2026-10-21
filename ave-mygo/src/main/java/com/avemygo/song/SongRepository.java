package com.avemygo.song;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 歌曲数据访问。
 * 动态筛选套路：可选参数用 ":x IS NULL OR ..." 短路，一条 SQL 吃下所有组合。
 * FIND_IN_SET：在逗号分隔的 tags 串中精确匹配标签（LIKE 会误伤"燃"匹配"点燃"）。
 */
public interface SongRepository extends JpaRepository<Song, Long> {

    /**
     * 前台歌曲列表：只查上架歌曲，支持乐队/关键词/标签筛选 + 分页。
     * 关键词同时匹配「曲名」与「乐队名」——用户搜 "MyGO" 期望看到该乐队全部曲目。
     */
    @Query("""
            SELECT s FROM Song s
            WHERE s.status = 1
              AND (:bandId IS NULL OR s.bandId = :bandId)
              AND (:keyword IS NULL
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR EXISTS (SELECT 1 FROM Band b WHERE b.id = s.bandId
                              AND LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))))
              AND (:tag IS NULL OR FUNCTION('FIND_IN_SET', :tag, s.tags) > 0)
            """)
    Page<Song> search(@Param("bandId") Short bandId,
                      @Param("keyword") String keyword,
                      @Param("tag") String tag,
                      Pageable pageable);

    /** 按乐队统计上架歌曲数（AI 助手知识问答用） */
    long countByBandIdAndStatus(Short bandId, Byte status);
}
