package com.avemygo.admin;

import com.avemygo.admin.dto.AdminEventDtos.EventPatch;
import com.avemygo.admin.dto.AdminEventDtos.EventUpsert;
import com.avemygo.admin.dto.AdminEventDtos.EventView;
import com.avemygo.admin.dto.AdminEventDtos.SessionPatch;
import com.avemygo.admin.dto.AdminEventDtos.SessionUpsert;
import com.avemygo.admin.dto.AdminEventDtos.SessionView;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypePatch;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypeUpsert;
import com.avemygo.admin.dto.AdminEventDtos.TicketTypeView;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端演出域接口（/api/v1/admin/** 仅 ADMIN）。
 * 演出：GET/POST 列表与新建，PATCH 修改，DELETE 软删（有未完成订单拒绝）
 * 场次：GET/POST /events/{id}/sessions，PATCH /sessions/{id}，DELETE 软删并级联票档/曲目单
 * 票档：POST /sessions/{id}/ticket-types，PATCH /ticket-types/{id}（含 status 下架/恢复）
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService service;

    // ---------------- 演出 ----------------

    @GetMapping("/events")
    public ApiResponse<Page<EventView>> events(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short bandId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.events(status, bandId, page, pageSize));
    }

    @PostMapping("/events")
    public ApiResponse<EventView> createEvent(@Valid @RequestBody EventUpsert req) {
        return ApiResponse.ok(service.create(req));
    }

    @PatchMapping("/events/{id}")
    public ApiResponse<EventView> updateEvent(@PathVariable Long id, @Valid @RequestBody EventPatch req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> deleteEvent(@PathVariable Long id) {
        service.deleteEvent(id);
        return ApiResponse.ok();
    }

    // ---------------- 场次 ----------------

    @GetMapping("/events/{id}/sessions")
    public ApiResponse<List<SessionView>> sessions(@PathVariable Long id) {
        return ApiResponse.ok(service.sessions(id));
    }

    @PostMapping("/events/{id}/sessions")
    public ApiResponse<SessionView> createSession(@PathVariable Long id,
                                                  @Valid @RequestBody SessionUpsert req) {
        return ApiResponse.ok(service.createSession(id, req));
    }

    @PatchMapping("/sessions/{id}")
    public ApiResponse<SessionView> updateSession(@PathVariable Long id,
                                                  @Valid @RequestBody SessionPatch req) {
        return ApiResponse.ok(service.updateSession(id, req));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        service.deleteSession(id);
        return ApiResponse.ok();
    }

    // ---------------- 票档 ----------------

    @PostMapping("/sessions/{id}/ticket-types")
    public ApiResponse<TicketTypeView> createTicketType(@PathVariable Long id,
                                                        @Valid @RequestBody TicketTypeUpsert req) {
        return ApiResponse.ok(service.createTicketType(id, req));
    }

    @PatchMapping("/ticket-types/{id}")
    public ApiResponse<TicketTypeView> updateTicketType(@PathVariable Long id,
                                                        @Valid @RequestBody TicketTypePatch req) {
        return ApiResponse.ok(service.updateTicketType(id, req));
    }
}
