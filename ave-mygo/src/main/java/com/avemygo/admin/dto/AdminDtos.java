package com.avemygo.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
            @NotBlank(message = "歌曲名不能为空") @Size(max = 100) String title,
            @NotNull(message = "乐队不能为空") Short bandId,
            Long albumId,
            @Min(value = 1, message = "曲序至少为 1") @Max(value = 99) Integer trackNo,
            LocalDate releaseDate,
            @Min(value = 1, message = "时长必须大于 0") Integer duration,
            @Size(max = 255) String coverUrl,
            @Size(max = 512) String audioUrl,
            @Size(max = 512) String mvUrl,
            @Size(max = 20) String mvPlatform,
            @Size(max = 255) String tags,
            @Min(value = 0, message = "状态只能是 0/1") @Max(value = 1) Byte status
    ) {
    }

    /** 部分更新请求：null 字段不改动 */
    public record SongPatch(
            @Size(max = 100) String title,
            Long albumId,
            @Min(value = 1) @Max(value = 99) Integer trackNo,
            LocalDate releaseDate,
            @Min(value = 1, message = "时长必须大于 0") Integer duration,
            @Size(max = 255) String coverUrl,
            @Size(max = 512) String audioUrl,
            @Size(max = 512) String mvUrl,
            @Size(max = 20) String mvPlatform,
            @Size(max = 255) String tags,
            @Min(value = 0, message = "状态只能是 0/1") @Max(value = 1) Byte status
    ) {
    }
}
