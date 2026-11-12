package com.avemygo.admin;

import com.avemygo.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hero 背景视频上传（docs/05 M4.8 管理端）。
 * POST /api/v1/admin/hero-videos（multipart：slot=mygo|mujica，file=mp4 ≤100MB）
 * 写入 hero.assets-dir 下的 band-{slot}-720p.mp4，先落临时文件再原子替换，
 * 避免上传途中前端拉到半截视频。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class HeroVideoController {

    private final HeroVideoService heroVideoService;

    @PostMapping("/hero-videos")
    public ApiResponse<Void> upload(@RequestParam("slot") String slot,
                                    @RequestParam("file") MultipartFile file) {
        heroVideoService.upload(slot, file);
        return ApiResponse.ok(null);
    }
}
