package com.avemygo.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 数据看板 DTO：一次请求返回总览 + 趋势 + 场次上座率，前端不必多次拉取。 */
public class AdminStatsDtos {

    /** 总额卡片 */
    public record Totals(
            long orders,          // 已支付订单数
            BigDecimal revenue,   // 已支付且未退款金额
            long ticketsSold,     // 已售出票数（含已退款？否：只算有效）
            long refundedOrders,  // 已退款订单数（供对比）
            long users            // 注册用户数
    ) {
    }

    /** 每日趋势（缺失日期补 0，前端可直接画折线） */
    public record DailyPoint(
            LocalDate date, long orders, BigDecimal revenue
    ) {
    }

    /** 场次上座率 */
    public record SessionOccupancy(
            Long sessionId, String sessionName, String eventTitle,
            LocalDateTime startTime, int totalStock, int soldCount, double occupancy
    ) {
    }

    public record Overview(
            Totals totals,
            List<DailyPoint> daily,
            List<SessionOccupancy> sessions
    ) {
    }
}
