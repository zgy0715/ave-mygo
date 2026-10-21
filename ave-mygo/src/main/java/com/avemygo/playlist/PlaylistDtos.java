package com.avemygo.playlist;

import com.avemygo.song.dto.SongDtos.SongItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 歌单 DTO。 */
public class PlaylistDtos {

    /** 列表卡片：曲目数 + 封面（取第一首） */
    public record PlaylistSummary(
            Long id, String name, String description,
            Integer songCount, String coverUrl, LocalDateTime updatedAt
    ) {
    }

    /** 歌单详情：按 sort 顺序返回完整歌曲项（可直接喂给播放器 playQueue） */
    public record PlaylistDetail(
            Long id, String name, String description,
            List<SongItem> songs, LocalDateTime updatedAt
    ) {
    }

    public record CreateRequest(
            @NotBlank(message = "歌单名不能为空") @Size(max = 50, message = "歌单名最多 50 字") String name,
            @Size(max = 200, message = "简介最多 200 字") String description
    ) {
    }

    public record UpdateRequest(
            @Size(max = 50, message = "歌单名最多 50 字") String name,
            @Size(max = 200, message = "简介最多 200 字") String description
    ) {
    }

    public record AddSongRequest(
            @NotNull(message = "歌曲不能为空") Long songId
    ) {
    }
}
