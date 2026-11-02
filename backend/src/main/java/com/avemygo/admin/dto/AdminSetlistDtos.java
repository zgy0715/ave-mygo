package com.avemygo.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 管理端歌单录入 DTO：PUT 为全量替换语义（前端提交完整列表）。 */
public class AdminSetlistDtos {

    public record ReplaceRequest(
            @NotNull(message = "曲目列表不能为空") @Size(max = 50, message = "单场歌单最多 50 首")
            @Valid List<Item> items
    ) {
    }

    public record Item(
            @NotNull(message = "歌曲不能为空") Long songId,
            @Min(value = 1, message = "曲序从 1 开始") Integer orderNo,
            Boolean encore
    ) {
    }
}
