package com.avemygo.search;

import com.avemygo.song.dto.SongDtos.SongItem;
import java.time.LocalDateTime;
import java.util.List;

/** 全局搜索响应：歌曲 + 演出两组，前端下拉直接分组渲染。 */
public record SearchResult(
        List<SongItem> songs,
        List<EventBrief> events
) {
    /** 演出精简信息：带下一场开演时间，用户能判断"还能不能去" */
    public record EventBrief(
            Long id, String title, String venue, String status,
            String posterUrl, LocalDateTime nextStartTime
    ) {
    }
}
