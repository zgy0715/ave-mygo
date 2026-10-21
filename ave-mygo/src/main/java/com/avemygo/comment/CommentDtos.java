package com.avemygo.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 评论 DTO。 */
public class CommentDtos {

    /** own = 当前登录用户是否可删（本人或 ADMIN），前端据此显示删除按钮 */
    public record CommentView(
            Long id, Long userId, String nickname,
            String content, LocalDateTime createdAt, boolean own
    ) {
    }

    public record CreateRequest(
            @NotNull(message = "歌曲不能为空") Long songId,
            @NotBlank(message = "评论内容不能为空")
            @Size(max = 500, message = "评论最多 500 字") String content
    ) {
    }
}
