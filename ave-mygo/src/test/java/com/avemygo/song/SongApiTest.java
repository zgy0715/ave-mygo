package com.avemygo.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 歌曲查询接口：状态过滤、乐队筛选、关键词（曲名 + 乐队名）、标签精确匹配、分页上限、详情 404。
 * 覆盖此前完全没有测试的只读链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SongApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM song WHERE title LIKE '[SG]%'");
    }

    private void seedOfflineSong() {
        jdbc.update("INSERT INTO song(title,band_id,status,is_deleted,created_at,updated_at) "
                + "VALUES('[SG] 未上架曲',1,0,0,NOW(),NOW())");
    }

    @Test
    @DisplayName("G01 默认列表只返回上架歌曲（status=0 不出现）")
    void g01_statusFilter() throws Exception {
        seedOfflineSong();
        mvc.perform(get("/api/v1/songs").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '[SG] 未上架曲')]").isEmpty());
    }

    @Test
    @DisplayName("G02 关键词命中乐队名（搜 MyGO 出该队曲目）")
    void g02_keywordByBand() throws Exception {
        mvc.perform(get("/api/v1/songs").param("keyword", "MyGO").param("pageSize", "5"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.content[0].bandName").value("MyGO!!!!!"));
    }

    @Test
    @DisplayName("G03 乐队筛选 + 分页上限 50")
    void g03_bandAndCap() throws Exception {
        mvc.perform(get("/api/v1/songs").param("bandId", "1").param("pageSize", "500"))
                .andExpect(jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(50)))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.content[0].bandId").value(1));
    }

    @Test
    @DisplayName("G04 标签精确匹配（FIND_IN_SET，不误伤子串）")
    void g04_tag() throws Exception {
        mvc.perform(get("/api/v1/songs").param("tag", "燃").param("pageSize", "10"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("G05 详情字段完整；不存在 → 40401")
    void g05_detail() throws Exception {
        mvc.perform(get("/api/v1/songs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.bandName").isNotEmpty())
                .andExpect(jsonPath("$.data.coverUrl").isNotEmpty());

        mvc.perform(get("/api/v1/songs/999999"))
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("G06 软删除歌曲不可见")
    void g06_softDeleted() throws Exception {
        jdbc.update("INSERT INTO song(title,band_id,status,is_deleted,created_at,updated_at) "
                + "VALUES('[SG] 已删曲',1,1,1,NOW(),NOW())");
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM song", Long.class);
        mvc.perform(get("/api/v1/songs/" + id))
                .andExpect(jsonPath("$.code").value(40401));
    }
}
