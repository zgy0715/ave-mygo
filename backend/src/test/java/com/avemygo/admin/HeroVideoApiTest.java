package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.JwtService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=8KB"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HeroVideoApiTest {

    private static final Path ASSETS = Path.of("target", "hero-test-" + UUID.randomUUID()).toAbsolutePath();
    private static final byte[] ORIGINAL = { 1, 2, 3 };

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Value("${local.server.port}") private int port;
    private String adminToken;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("hero.assets-dir", ASSETS::toString);
        registry.add("spring.servlet.multipart.location", ASSETS::toString);
        registry.add("server.tomcat.basedir", () -> ASSETS.resolveSibling(ASSETS.getFileName() + "-tomcat").toString());
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        Files.createDirectories(ASSETS);
        Files.write(ASSETS.resolve("band-mygo-720p.mp4"), ORIGINAL);
        Files.write(ASSETS.resolve("band-mujica-720p.mp4"), ORIGINAL);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (Files.isDirectory(ASSETS)) {
            try (var files = Files.list(ASSETS)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(ASSETS);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "mygo", "mujica" })
    @DisplayName("上传成功替换首页读取的720p文件，并保留另一槽位")
    void uploadReplacesHomepageVideo(String slot) throws Exception {
        if (Files.getFileAttributeView(ASSETS, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(ASSETS, PosixFilePermissions.fromString("rwx------"));
        }
        byte[] content = { 0, 0, 0, 20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0, 'i', 's', 'o', 'm' };
        mvc.perform(multipart("/api/v1/admin/hero-videos")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", content))
                        .param("slot", slot).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Path target = ASSETS.resolve("band-" + slot + "-720p.mp4");
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
        if (Files.getFileAttributeView(ASSETS, PosixFileAttributeView.class) != null) {
            assertThat(Files.getPosixFilePermissions(ASSETS)).isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
            assertThat(Files.getPosixFilePermissions(target)).isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));
        }
        String other = "mygo".equals(slot) ? "mujica" : "mygo";
        assertThat(Files.readAllBytes(ASSETS.resolve("band-" + other + "-720p.mp4"))).isEqualTo(ORIGINAL);
        try (var files = Files.list(ASSETS)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("band-mygo-720p.mp4", "band-mujica-720p.mp4");
        }
    }

    @ParameterizedTest
    @CsvSource({ "clip.webm,video/webm", "fake.mp4,text/html" })
    @DisplayName("非 MP4 上传不能伪装成首页 MP4 文件")
    void rejectsOtherFormats(String filename, String type) throws Exception {
        mvc.perform(multipart("/api/v1/admin/hero-videos")
                        .file(new MockMultipartFile("file", filename, type, new byte[] { 7, 8, 9 }))
                        .param("slot", "mygo").header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(Files.readAllBytes(ASSETS.resolve("band-mygo-720p.mp4"))).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("伪装为 MP4 的文本不能覆盖原视频，也不遗留临时文件")
    void rejectsDisguisedMp4() throws Exception {
        mvc.perform(multipart("/api/v1/admin/hero-videos")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", "not an MP4 video".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("slot", "mygo").header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(Files.readAllBytes(ASSETS.resolve("band-mygo-720p.mp4"))).isEqualTo(ORIGINAL);
        try (var files = Files.list(ASSETS)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "avif", "heic" })
    @DisplayName("改名为 MP4 的 ISO BMFF 图片不能覆盖原视频")
    void rejectsImageDisguisedAsMp4(String brand) throws Exception {
        byte[] content = { 0, 0, 0, 20, 'f', 't', 'y', 'p', 0, 0, 0, 0, 0, 0, 0, 0, 'i', 's', 'o', 'm' };
        System.arraycopy(brand.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, content, 8, 4);
        mvc.perform(multipart("/api/v1/admin/hero-videos")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", content))
                        .param("slot", "mygo").header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(Files.readAllBytes(ASSETS.resolve("band-mygo-720p.mp4"))).isEqualTo(ORIGINAL);
        try (var files = Files.list(ASSETS)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("保存中断时保留原文件并清理临时文件")
    void failedUploadPreservesOriginal() throws Exception {
        MockMultipartFile interrupted = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] { 7 }) {
            @Override
            public void transferTo(Path dest) throws IOException {
                Files.write(dest, new byte[] { 9 });
                throw new IOException("[TEST] 上传中断");
            }
        };
        mvc.perform(multipart("/api/v1/admin/hero-videos").file(interrupted)
                        .param("slot", "mygo").header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(50001));
        assertThat(Files.readAllBytes(ASSETS.resolve("band-mygo-720p.mp4"))).isEqualTo(ORIGINAL);
        try (var files = Files.list(ASSETS)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("真实 HTTP 超过 multipart 限额时返回参数错误，保留原视频")
    void multipartLimitReturnsParameterError() throws Exception {
        String boundary = "hero-test-boundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"slot\"\r\n\r\nmygo\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.mp4\"\r\n"
                + "Content-Type: video/mp4\r\n\r\n" + "x".repeat(2048) + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/admin/hero-videos"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", adminToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(response.body()).path("code").asInt()).isEqualTo(40001);
        assertThat(Files.readAllBytes(ASSETS.resolve("band-mygo-720p.mp4"))).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("游客与普通用户不能上传，非法槽位与空文件不改视频")
    void rejectsUnauthorizedAndInvalidInput() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] { 7 });
        mvc.perform(multipart("/api/v1/admin/hero-videos").file(file).param("slot", "mygo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/admin/hero-videos").file(file).param("slot", "mygo")
                        .header("Authorization", "Bearer " + jwtService.generate(999L, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/admin/hero-videos").file(file).param("slot", "../outside")
                        .header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(multipart("/api/v1/admin/hero-videos")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[0]))
                        .param("slot", "mygo").header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(Files.readAllBytes(ASSETS.resolve("band-mygo-720p.mp4"))).isEqualTo(ORIGINAL);
    }
}
