package com.avemygo.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单相关请求/响应 DTO */
public class OrderDtos {

    /** 下单请求 */
    public record CreateOrderRequest(
            @NotNull(message = "场次不能为空") Long sessionId,
            @NotNull(message = "票档不能为空") Long ticketTypeId,
            @NotNull(message = "数量不能为空")
            @Min(value = 1, message = "至少购买 1 张")
            @Max(value = 10, message = "单笔最多 10 张")
            Integer quantity
    ) {
    }

    /** 下单响应：告诉前端去哪个倒计时、付多少钱 */
    public record OrderCreated(
            String orderNo, String status,
            BigDecimal unitPrice, BigDecimal totalAmount,
            Integer quantity,
            LocalDateTime payDeadline
    ) {
    }

    /** 订单视图（列表/详情/支付/取消通用） */
    public record OrderView(
            String orderNo, String status,
            Integer quantity, BigDecimal unitPrice, BigDecimal totalAmount,
            LocalDateTime payDeadline, LocalDateTime paidAt,
            LocalDateTime cancelledAt, LocalDateTime refundedAt, String refundReason,
            LocalDateTime createdAt
    ) {
    }
}
