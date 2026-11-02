package com.avemygo.playlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 歌单曲目。sort 决定播放顺序（新增时取当前最大值 +1）。
 * 唯一索引 uk_playlist_song 防止同一首歌重复加入同一歌单。
 */
@Getter
@Setter
@Entity
@Table(name = "playlist_item")
public class PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(nullable = false)
    private Integer sort = 0;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
