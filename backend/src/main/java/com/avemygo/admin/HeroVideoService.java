package com.avemygo.admin;

import com.avemygo.common.BizException;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 首页背景视频存储；保存成功后只替换首页实际读取的槽位文件。 */
@Service
@Slf4j
public class HeroVideoService {

    private static final Set<String> SLOTS = Set.of("mygo", "mujica");
    // MP4RA Brands 注册表中的常见 MP4 主品牌，避免将 AVIF/HEIC 图片容器当视频发布。
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
            "mp41", "mp42", "avc1", "dash", "M4V ", "MSNV");
    private static final long MAX_SIZE = 100L * 1024 * 1024;
    private final Path assetsDir;

    public HeroVideoService(@Value("${hero.assets-dir:../frontend/public/assets}") String assetsDir) {
        this.assetsDir = Path.of(assetsDir).toAbsolutePath().normalize();
    }

    public void upload(String slot, MultipartFile file) {
        if (slot == null || !SLOTS.contains(slot)) throw new BizException(40001, "slot 只支持 mygo / mujica");
        if (file == null || file.isEmpty()) throw new BizException(40001, "请选择要上传的视频文件");
        if (file.getSize() > MAX_SIZE) throw new BizException(40001, "视频不能超过 100MB");
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean compatibleType = contentType == null || contentType.isBlank()
                || "video/mp4".equalsIgnoreCase(contentType) || "application/octet-stream".equalsIgnoreCase(contentType);
        if (!filename.endsWith(".mp4") || !compatibleType) throw new BizException(40001, "只支持 MP4 视频文件");

        Path tmp = null;
        try {
            Files.createDirectories(assetsDir);
            setPublicPermissions(assetsDir, "rwxr-xr-x");
            Path target = assetsDir.resolve("band-" + slot + "-720p.mp4");
            // 同槽位并发上传也各自拥有临时文件，不会将另一请求的半截内容移到目标。
            tmp = Files.createTempFile(assetsDir, "band-" + slot + "-", ".uploading");
            file.transferTo(tmp);
            validateMp4Header(tmp);
            // 临时文件默认可能是 0600；发布前允许 Nginx 读取，Windows 不设置 POSIX 权限。
            setPublicPermissions(tmp, "rw-r--r--");
            // 文件系统不支持原子替换时直接失败，避免普通替换中断后损坏原视频。
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Hero 视频保存失败: slot={}", slot, e);
            throw new BizException(50001, "视频保存失败，请稍后重试");
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    log.warn("清理 Hero 上传临时文件失败: {}", tmp, e);
                }
            }
        }
    }

    /** 轻量检查 ISO BMFF 的 ftyp 头与主品牌；不解码，也不保证浏览器支持视频编码。 */
    private void validateMp4Header(Path file) throws IOException {
        long size = Files.size(file);
        if (size < 16) throw new BizException(40001, "只支持 MP4 视频文件");
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            long boxSize = Integer.toUnsignedLong(input.readInt());
            int type = input.readInt();
            int headerSize = 8;
            if (boxSize == 1) {
                boxSize = input.readLong();
                headerSize = 16;
            }
            if (type != 0x66747970 || boxSize < headerSize + 8 || boxSize > size
                    || (boxSize - headerSize) % 4 != 0) {
                throw new BizException(40001, "只支持 MP4 视频文件");
            }
            String majorBrand = new String(input.readNBytes(4), StandardCharsets.US_ASCII);
            if (!MP4_BRANDS.contains(majorBrand)) throw new BizException(40001, "只支持 MP4 视频文件");
        }
    }

    private void setPublicPermissions(Path path, String permissions) throws IOException {
        var view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) view.setPermissions(PosixFilePermissions.fromString(permissions));
    }
}
