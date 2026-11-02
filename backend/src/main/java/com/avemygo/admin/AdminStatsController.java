package com.avemygo.admin;

import com.avemygo.admin.dto.AdminStatsDtos.Overview;
import com.avemygo.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端数据看板（仅 ADMIN）：总览 + 近 N 天趋势 + 场次上座率。 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService service;

    @GetMapping("/overview")
    public ApiResponse<Overview> overview(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(service.overview(days));
    }
}
