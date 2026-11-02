package com.avemygo.playlist;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/** 歌单曲目数据访问。 */
public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    List<PlaylistItem> findByPlaylistIdOrderBySortAscIdAsc(Long playlistId);

    Optional<PlaylistItem> findByPlaylistIdAndSongId(Long playlistId, Long songId);

    long countByPlaylistId(Long playlistId);

    /** 批量统计多个歌单的曲目数（列表页防 N+1） */
    @Query("""
            SELECT i.playlistId, COUNT(i) FROM PlaylistItem i
            WHERE i.playlistId IN :playlistIds GROUP BY i.playlistId
            """)
    List<Object[]> countGrouped(@Param("playlistIds") Collection<Long> playlistIds);

    /** 新增曲目时的最大 sort（无曲目返回 null） */
    @Query("SELECT MAX(i.sort) FROM PlaylistItem i WHERE i.playlistId = :playlistId")
    Integer maxSort(@Param("playlistId") Long playlistId);

    @Modifying
    void deleteByPlaylistId(Long playlistId);

    @Modifying
    void deleteByPlaylistIdAndSongId(Long playlistId, Long songId);
}
