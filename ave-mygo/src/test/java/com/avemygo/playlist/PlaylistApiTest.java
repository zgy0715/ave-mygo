package com.avemygo.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.AuthService;
import com.avemygo.auth.JwtService;
import com.avemygo.auth.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** 歌单：增删改查 + 加歌/移歌幂等 + 归属隔离（别人的歌单 40401）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaylistApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    private String token;
    private String otherToken;
    private Long userId;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userId = authService.register(new RegisterRequest("pl_" + System.currentTimeMillis() % 1000000, "pass1234", null));
        Long otherId = authService.register(new RegisterRequest("pl2_" + System.currentTimeMillis() % 1000000, "pass1234", null));
        token = "Bearer " + jwtService.generate(userId, "USER");
        otherToken = "Bearer " + jwtService.generate(otherId, "USER");
        jdbc.update("DELETE FROM playlist_item WHERE playlist_id IN (SELECT id FROM playlist WHERE user_id IN (?,?))", userId, otherId);
        jdbc.update("DELETE FROM playlist WHERE user_id IN (?,?)", userId, otherId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM playlist_item WHERE playlist_id IN (SELECT id FROM playlist WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'pl_%' OR username LIKE 'pl2_%'))");
        jdbc.update("DELETE FROM playlist WHERE user_id IN (SELECT id FROM user WHERE username LIKE 'pl_%' OR username LIKE 'pl2_%')");
        jdbc.update("DELETE FROM user WHERE username LIKE 'pl_%' OR username LIKE 'pl2_%'");
    }

    private MvcResult postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private long createPlaylist(String name) throws Exception {
        MvcResult r = postJson("/api/v1/playlists", "{\"name\":\"" + name + "\",\"description\":\"自测\"}");
        return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    @Test
    @DisplayName("P01 创建歌单 → 列表可见，空歌单曲目数 0")
    void p01_create() throws Exception {
        long id = createPlaylist("我的第一张歌单");
        assertThat(id).isPositive();

        mvc.perform(get("/api/v1/playlists").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("我的第一张歌单"))
                .andExpect(jsonPath("$.data[0].songCount").value(0));
    }

    @Test
    @DisplayName("P02 加歌幂等 + 详情按加入顺序返回完整 SongItem")
    void p02_addSongs() throws Exception {
        long id = createPlaylist("加歌测试");
        postJson("/api/v1/playlists/" + id + "/songs", "{\"songId\":1}");
        postJson("/api/v1/playlists/" + id + "/songs", "{\"songId\":2}");
        postJson("/api/v1/playlists/" + id + "/songs", "{\"songId\":1}");   // 重复加入

        mvc.perform(get("/api/v1/playlists/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.data.songs.length()").value(2))
                .andExpect(jsonPath("$.data.songs[0].id").value(1))
                .andExpect(jsonPath("$.data.songs[1].id").value(2))
                .andExpect(jsonPath("$.data.songs[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data.songs[0].bandName").isNotEmpty());
    }

    @Test
    @DisplayName("P03 移出歌曲幂等；列表 songCount 同步")
    void p03_removeSong() throws Exception {
        long id = createPlaylist("移歌测试");
        postJson("/api/v1/playlists/" + id + "/songs", "{\"songId\":1}");

        mvc.perform(delete("/api/v1/playlists/" + id + "/songs/1").header("Authorization", token))
                .andExpect(jsonPath("$.data.songs.length()").value(0));
        mvc.perform(delete("/api/v1/playlists/" + id + "/songs/1").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/playlists").header("Authorization", token))
                .andExpect(jsonPath("$.data[0].songCount").value(0));
    }

    @Test
    @DisplayName("P04 重名/空名 → 40001；改名成功")
    void p04_renameAndGuards() throws Exception {
        long id = createPlaylist("重名测试");
        post("/api/v1/playlists", "{\"name\":\"重名测试\"}");   // 同名
        MvcResult dup = postJson("/api/v1/playlists", "{\"name\":\"重名测试\"}");
        assertThat(mapper.readTree(dup.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("code").asInt()).isEqualTo(40001);

        mvc.perform(patch("/api/v1/playlists/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"改名后\"}"))
                .andExpect(jsonPath("$.data.name").value("改名后"));

        mvc.perform(post("/api/v1/playlists").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("P05 越权访问别人的歌单 → 40401")
    void p05_ownership() throws Exception {
        long id = createPlaylist("私有歌单");
        mvc.perform(get("/api/v1/playlists/" + id).header("Authorization", otherToken))
                .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(delete("/api/v1/playlists/" + id).header("Authorization", otherToken))
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("P06 删除歌单连同曲目一起清；未登录 401")
    void p06_delete() throws Exception {
        long id = createPlaylist("待删歌单");
        postJson("/api/v1/playlists/" + id + "/songs", "{\"songId\":1}");

        mvc.perform(delete("/api/v1/playlists/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.data.deleted").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM playlist_item WHERE playlist_id=?", Integer.class, id))
                .isZero();

        mvc.perform(get("/api/v1/playlists")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P07 加入不存在的歌曲 → 40401")
    void p07_badSong() throws Exception {
        long id = createPlaylist("坏歌测试");
        MvcResult r = postJson("/api/v1/playlists/" + id + "/songs", "{\"songId\":999999}");
        assertThat(mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("code").asInt()).isEqualTo(40401);
    }
}
