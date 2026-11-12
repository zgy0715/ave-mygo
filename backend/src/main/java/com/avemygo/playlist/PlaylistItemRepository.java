package com.avemygo.playlist;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 歌单曲目数据访问。 */
public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    List<PlaylistItem> findByPlaylistIdOrderBySortAscIdAsc(Long playlistId);

    Optional<PlaylistItem> findByPlaylistIdAndSongId(Long playlistId, Long songId);

    @Query("""
            SELECT COUNT(i) FROM PlaylistItem i JOIN Song s ON s.id = i.songId
            WHERE i.playlistId = :playlistId AND s.status = 1
            """)
    long countByPlaylistId(@Param("playlistId") Long playlistId);

    /** 批量取得可见曲目及封面，按歌单和曲序返回（列表页防 N+1）。 */
    @Query("""
            SELECT i.playlistId, s.coverUrl FROM PlaylistItem i JOIN Song s ON s.id = i.songId
            WHERE i.playlistId IN :playlistIds AND s.status = 1
            ORDER BY i.playlistId, i.sort, i.id
            """)
    List<Object[]> visibleSummaryRows(@Param("playlistIds") Collection<Long> playlistIds);

    /** 新增曲目时的最大 sort（无曲目返回 null） */
    @Query("SELECT MAX(i.sort) FROM PlaylistItem i WHERE i.playlistId = :playlistId")
    Integer maxSort(@Param("playlistId") Long playlistId);

    @Modifying
    void deleteByPlaylistId(Long playlistId);

    @Modifying
    void deleteByPlaylistIdAndSongId(Long playlistId, Long songId);
}
