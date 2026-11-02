package com.avemygo.admin;

import com.avemygo.admin.dto.AdminSetlistDtos.ReplaceRequest;
import com.avemygo.common.ApiResponse;
import com.avemygo.event.dto.EventDtos.SetlistItemView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端歌单接口（仅 ADMIN）：读取 + 全量替换。 */
@RestController
@RequestMapping("/api/v1/admin/sessions/{sessionId}/setlist")
@RequiredArgsConstructor
public class AdminSetlistController {

    private final AdminSetlistService service;

    @GetMapping
    public ApiResponse<List<SetlistItemView>> get(@PathVariable Long sessionId) {
        return ApiResponse.ok(service.get(sessionId));
    }

    @PutMapping
    public ApiResponse<List<SetlistItemView>> replace(@PathVariable Long sessionId,
                                                      @Valid @RequestBody ReplaceRequest req) {
        return ApiResponse.ok(service.replace(sessionId, req.items()));
    }
}
