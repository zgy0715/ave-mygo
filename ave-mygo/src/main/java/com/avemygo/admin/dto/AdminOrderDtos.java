package com.avemygo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端订单 DTO：视图带出可读名称（用户名/演出/场次/票档），前端无需二次查询。 */
public class AdminOrderDtos {

    public record AdminOrderView(
            String orderNo,
            Long userId,
            String username,
            String eventTitle,
            String sessionName,
            String ticketTypeName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String status,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            LocalDateTime payDeadline,
            LocalDateTime refundedAt,
            String refundReason
    ) {
    }

    /** 退款请求：原因必填（2-100 字），用于审计 */
    public record RefundRequest(
            @NotBlank(message = "退款原因不能为空")
            @Size(min = 2, max = 100, message = "退款原因 2-100 字")
            String reason
    ) {
    }
}
