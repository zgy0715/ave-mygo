package com.avemygo.admin.dto;

import java.time.LocalDate;

/** 管理端歌曲 DTO */
public class AdminDtos {

    /** 管理视图：全字段（含下架/封面/音源/MV），区别于前台精简视图 */
    public record AdminSongView(
            Long id, String title, Short bandId, String bandName,
            Long albumId, Integer trackNo, LocalDate releaseDate,
            Integer duration, String coverUrl, String audioUrl,
            String mvUrl, String mvPlatform, String tags,
            Byte status
    ) {
    }

    /** 新建请求：title/bandId 必填，其余可选 */
    public record SongUpsert(
            String title, Short bandId, Long albumId, Integer trackNo,
            LocalDate releaseDate, Integer duration,
            String coverUrl, String audioUrl, String mvUrl, String mvPlatform,
            String tags, Byte status
    ) {
    }

    /** 部分更新请求：null 字段不改动 */
    public record SongPatch(
            String title, Long albumId, Integer trackNo,
            LocalDate releaseDate, Integer duration,
            String coverUrl, String audioUrl, String mvUrl, String mvPlatform,
            String tags, Byte status
    ) {
    }
}
