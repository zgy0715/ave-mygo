package com.avemygo.admin;

import com.avemygo.common.ApiResponse;
import com.avemygo.common.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hero 背景视频上传（docs/05 M4.8 管理端）。
 * POST /api/v1/admin/hero-videos（multipart：slot=mygo|mujica，file=mp4 ≤100MB）
 * 写入 frontend/public/assets/band-{slot}.mp4，先落临时文件再原子替换，
 * 避免上传途中前端拉到半截视频。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class HeroVideoController {

    private static final Set<String> SLOTS = Set.of("mygo", "mujica");
    private static final long MAX_SIZE = 100L * 1024 * 1024;

    /** 前端静态资产目录（相对后端工作目录；上生产改环境变量指向挂载卷） */
    @Value("${hero.assets-dir:../frontend/public/assets}")
    private String assetsDir;

    @PostMapping("/hero-videos")
    public ApiResponse<Void> upload(@RequestParam("slot") String slot,
                                    @RequestParam("file") MultipartFile file) {
        if (slot == null || !SLOTS.contains(slot)) {
            throw new BizException(40001, "slot 只支持 mygo / mujica");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(40001, "请选择要上传的视频文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(40001, "视频不能超过 100MB");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean isVideo = (contentType != null && contentType.startsWith("video/"))
                || filename.endsWith(".mp4");
        if (!isVideo) {
            throw new BizException(40001, "只支持 MP4 视频文件");
        }

        Path dir = Paths.get(assetsDir).toAbsolutePath().normalize();
        Path target = dir.resolve("band-" + slot + ".mp4");
        Path tmp = dir.resolve("band-" + slot + ".mp4.uploading");
        try {
            Files.createDirectories(dir);
            file.transferTo(tmp);                                        // 先落临时文件
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE); // 同目录原子替换
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(50001, "视频保存失败，请稍后重试");
        }
        return ApiResponse.ok(null);
    }
}
