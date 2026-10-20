package com.avemygo.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端演出域 DTO（演出 / 场次 / 票档）。
 * 与前台 DTO 分离：管理端可见 DRAFT/ENDED、可写库存与时间，前台只展示可售数据。
 * 所有写请求在入口层做参数校验（@Valid），不信任客户端。
 */
public class AdminEventDtos {

    // ---------------- 演出 ----------------

    public record EventView(
            Long id, String title, Short bandId, String bandName,
            String description, String posterUrl, String venue, String status,
            Integer sessionCount
    ) {
    }

    public record EventUpsert(
            @NotBlank(message = "演出标题不能为空") @Size(max = 100) String title,
            @NotNull(message = "乐队不能为空") Short bandId,
            @Size(max = 2000) String description,
            @Size(max = 255) String posterUrl,
            @NotBlank(message = "场馆不能为空") @Size(max = 100) String venue,
            String status                       // DRAFT / PUBLISHED / ENDED，缺省 DRAFT
    ) {
    }

    public record EventPatch(
            @Size(max = 100) String title,
            Short bandId,
            @Size(max = 2000) String description,
            @Size(max = 255) String posterUrl,
            @Size(max = 100) String venue,
            String status
    ) {
    }

    // ---------------- 场次 ----------------

    public record SessionView(
            Long id, Long eventId, String name,
            LocalDateTime startTime, LocalDateTime endTime,
            LocalDateTime saleStartTime, String status,
            java.util.List<TicketTypeView> ticketTypes
    ) {
    }

    public record SessionUpsert(
            @NotBlank(message = "场次名称不能为空") @Size(max = 50) String name,
            @NotNull(message = "开演时间不能为空") LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空") LocalDateTime endTime,
            LocalDateTime saleStartTime,        // 缺省 = 立即开售
            String status                       // NOT_OPEN / ONSALE / ENDED，缺省 NOT_OPEN
    ) {
    }

    public record SessionPatch(
            @Size(max = 50) String name,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime saleStartTime,
            String status
    ) {
    }

    // ---------------- 票档 ----------------

    public record TicketTypeView(
            Long id, Long sessionId, String name,
            BigDecimal price, Integer totalStock, Integer soldCount,
            Integer remaining, Byte maxPerUser, Byte status
    ) {
    }

    public record TicketTypeUpsert(
            @NotBlank(message = "票档名称不能为空") @Size(max = 50) String name,
            @NotNull(message = "价格不能为空") @DecimalMin(value = "0.01", message = "价格必须大于 0") BigDecimal price,
            @NotNull(message = "库存不能为空") @Min(value = 0, message = "库存不能为负") Integer totalStock,
            @Min(value = 1, message = "每人限购至少 1 张") @Max(value = 10, message = "每人限购最多 10 张") Integer maxPerUser,
            Byte status                         // 1 在售 / 0 停售，缺省 1
    ) {
    }

    /** 部分更新：库存调小不得低于已售数量（业务层校验） */
    public record TicketTypePatch(
            @Size(max = 50) String name,
            @DecimalMin(value = "0.01", message = "价格必须大于 0") BigDecimal price,
            @Min(value = 0, message = "库存不能为负") Integer totalStock,
            @Min(value = 1) @Max(value = 10) Integer maxPerUser,
            Byte status
    ) {
    }
}
