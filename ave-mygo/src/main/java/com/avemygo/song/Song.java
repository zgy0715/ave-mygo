package com.avemygo.song;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 歌曲实体，对应表 song（docs/05 §3.5）—— 歌曲库模块的核心。
 * 红线：audio_url/mv_url 只存官方外链，绝不托管版权音频。
 */
@Entity
@Table(name = "song")
@SQLRestriction("is_deleted = 0")   // 软删除：所有查询自动过滤已删除歌曲
@Getter
@Setter
@NoArgsConstructor
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 逻辑 FK → band.id */
    @Column(name = "band_id", nullable = false)
    private Short bandId;

    /** 逻辑 FK → album.id，可空（未收录单曲） */
    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "track_no")
    private Integer trackNo;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** 时长（秒） */
    @Column(nullable = false)
    private Integer duration;

    @Column(name = "cover_url")
    private String coverUrl;

    /** 试听音源外链；null = 仅支持 MV 跳转 */
    @Column(name = "audio_url")
    private String audioUrl;

    /** 官方平台全曲收听页（网易云等，仅外链） */
    @Column(name = "stream_url", length = 255)
    private String streamUrl;

    @Column(name = "mv_url")
    private String mvUrl;

    /** BILIBILI / YOUTUBE —— 决定前端 embed 渲染方式 */
    @Column(name = "mv_platform", length = 20)
    private String mvPlatform;

    /** 逗号分隔标签，如 燃,夜勤 —— 筛选与 AI 推荐依赖 */
    @Column(nullable = false, length = 200)
    private String tags = "";

    /** 1 上架 / 0 下架（下架后前台不可见） */
    @Column(nullable = false)
    private Byte status = (byte) 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Byte isDeleted = (byte) 0;
}
