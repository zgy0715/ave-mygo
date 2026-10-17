package com.avemygo.event;

import com.avemygo.common.ApiResponse;
import com.avemygo.event.dto.EventDtos.EventDetail;
import com.avemygo.event.dto.EventDtos.EventItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演出接口（公开，游客可浏览）。
 * GET /api/v1/events      列表：status/bandId 可选筛选
 * GET /api/v1/events/{id} 详情：场次 + 票档 + 实时余票
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ApiResponse<Page<EventItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short bandId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize) {
        return ApiResponse.ok(eventService.list(status, bandId, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(eventService.detail(id));
    }
}
