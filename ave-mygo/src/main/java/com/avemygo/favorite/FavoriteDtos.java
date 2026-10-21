package com.avemygo.favorite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 收藏 DTO：列表项带出可读标题/封面，前端不必再逐条查歌曲或演出。 */
public class FavoriteDtos {

    public record FavoriteItem(
            String targetType,
            Long targetId,
            String title,
            String coverUrl,
            String extra,
            LocalDateTime createdAt
    ) {
    }

    public record AddRequest(
            @NotBlank(message = "收藏类型不能为空") String targetType,
            @NotNull(message = "收藏对象不能为空") Long targetId
    ) {
    }
}
