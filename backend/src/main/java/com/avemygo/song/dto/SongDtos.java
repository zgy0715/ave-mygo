package com.avemygo.song.dto;

import java.time.LocalDate;

/** 歌曲相关响应 DTO：实体 → 前端视图的翻译层 */
public class SongDtos {

    /** 列表项 */
    public record SongItem(
            Long id, String title,
            Short bandId, String bandName,
            Long albumId, String albumName,
            Integer duration, String coverUrl,
            String audioUrl, String streamUrl, String mvUrl, String mvPlatform,
            String tags, LocalDate releaseDate
    ) {
    }

    /** 详情（比列表多专辑类型与 embed 可用性标记） */
    public record SongDetail(
            Long id, String title,
            Short bandId, String bandName,
            Long albumId, String albumName, String albumType,
            Integer trackNo, LocalDate releaseDate, Integer duration,
            String coverUrl, String audioUrl, String streamUrl,
            String mvUrl, String mvPlatform, Boolean mvEmbedReady,
            String tags
    ) {
    }
}
