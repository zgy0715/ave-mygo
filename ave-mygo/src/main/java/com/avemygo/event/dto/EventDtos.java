package com.avemygo.event.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 演出相关响应 DTO */
public class EventDtos {

    /** 列表项：minPrice / nextSessionTime 为实时派生字段 */
    public record EventItem(
            Long id, String title, String bandName,
            String posterUrl, String venue, String status,
            BigDecimal minPrice, LocalDateTime nextSessionTime
    ) {
    }

    /** 票档视图：availableStock 实时计算 */
    public record TicketTypeView(
            Long id, String name, BigDecimal price,
            Integer availableStock, Integer totalStock,
            Byte maxPerUser, Byte status
    ) {
    }

    /** 场次视图（含票档） */
    public record SessionView(
            Long id, String name,
            LocalDateTime startTime, LocalDateTime endTime,
            String status, LocalDateTime saleStartTime,
            List<TicketTypeView> ticketTypes
    ) {
    }

    /** 演出详情 */
    public record EventDetail(
            Long id, String title, String bandName, String description,
            String posterUrl, String venue, String status,
            List<SessionView> sessions
    ) {
    }

    /** 演出曲视图：songTitle/duration 由 song 表联取 */
    public record SetlistItemView(
            Integer orderNo, Boolean encore,
            Long songId, String songTitle, Integer duration
    ) {
    }
}
