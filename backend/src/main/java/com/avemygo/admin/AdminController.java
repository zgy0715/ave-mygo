package com.avemygo.admin;

import com.avemygo.admin.dto.AdminDtos.AdminSongView;
import com.avemygo.admin.dto.AdminDtos.SongPatch;
import com.avemygo.admin.dto.AdminDtos.SongUpsert;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
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
 * 管理端接口（SecurityConfig：/api/v1/admin/** 仅 ADMIN 可访问）。
 * GET    /api/v1/admin/songs      列表（含下架，bandId/keyword 筛选）
 * POST   /api/v1/admin/songs      新建歌曲
 * PATCH  /api/v1/admin/songs/{id} 部分更新（音源/MV 外链维护的主入口）
 * DELETE /api/v1/admin/songs/{id} 软删除
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/songs")
    public ApiResponse<Page<AdminSongView>> songs(
            @RequestParam(required = false) Short bandId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminService.songs(bandId, keyword, page, pageSize));
    }

    @PostMapping("/songs")
    public ApiResponse<AdminSongView> create(@Valid @RequestBody SongUpsert req) {
        return ApiResponse.ok(adminService.create(req));
    }

    @PatchMapping("/songs/{id}")
    public ApiResponse<AdminSongView> update(@PathVariable Long id, @Valid @RequestBody SongPatch req) {
        return ApiResponse.ok(adminService.update(id, req));
    }

    @DeleteMapping("/songs/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ApiResponse.ok(null);
    }
}
