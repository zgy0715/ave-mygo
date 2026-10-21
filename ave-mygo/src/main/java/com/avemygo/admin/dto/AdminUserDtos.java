package com.avemygo.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端用户视图与状态请求。 */
public class AdminUserDtos {

    public record UserView(
            Long id, String username, String nickname,
            String role, Byte status,
            long orderCount, BigDecimal totalSpent,
            LocalDateTime createdAt
    ) {
    }

    /** 0 = 封禁，1 = 正常 */
    public record StatusRequest(
            @NotNull(message = "状态不能为空")
            @Min(value = 0, message = "状态只能是 0 或 1")
            @Max(value = 1, message = "状态只能是 0 或 1")
            Byte status
    ) {
    }
}
