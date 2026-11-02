package com.avemygo.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 全局搜索：歌曲/演出双组、草稿排除、limit 上限、空关键词校验、匿名可访问。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[SR] 春日影纪念演出',1,'东京场馆','PUBLISHED',0,NOW(),NOW())");
        Long eid = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'昼场',DATE_ADD(NOW(),INTERVAL 10 DAY),DATE_ADD(NOW(),INTERVAL 10 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),'ONSALE',0,NOW(),NOW())", eid);
        // 草稿演出，必须搜不到
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[SR] 草稿春日影',1,'秘密场馆','DRAFT',0,NOW(),NOW())");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM live_session WHERE name='昼场' AND event_id IN (SELECT id FROM live_event WHERE title LIKE '[SR]%')");
        jdbc.update("DELETE FROM live_event WHERE title LIKE '[SR]%'");
    }

    @Test
    @DisplayName("S01 搜「春日影」：命中歌曲 + 已发布演出，草稿不出现")
    void s01_searchBoth() throws Exception {
        mvc.perform(get("/api/v1/search").param("keyword", "春日影"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.songs[0].title").value("春日影"))
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].title").value("[SR] 春日影纪念演出"))
                .andExpect(jsonPath("$.data.events[0].venue").value("东京场馆"))
                .andExpect(jsonPath("$.data.events[0].nextStartTime").isNotEmpty());
    }

    @Test
    @DisplayName("S02 搜乐队名命中多首；limit 生效")
    void s02_limit() throws Exception {
        mvc.perform(get("/api/v1/search").param("keyword", "MyGO").param("limit", "2"))
                .andExpect(jsonPath("$.data.songs.length()").value(2));
        mvc.perform(get("/api/v1/search").param("keyword", "MyGO").param("limit", "999"))
                .andExpect(jsonPath("$.data.songs.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(20)));
    }

    @Test
    @DisplayName("S03 空关键词 → 40001；无结果 → 两个空数组")
    void s03_guards() throws Exception {
        mvc.perform(get("/api/v1/search").param("keyword", "  "))
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/api/v1/search").param("keyword", "zzz不存在zzz"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.songs.length()").value(0))
                .andExpect(jsonPath("$.data.events.length()").value(0));
    }

    @Test
    @DisplayName("S04 匿名可搜索（不要求登录）")
    void s04_public() throws Exception {
        mvc.perform(get("/api/v1/search").param("keyword", "迷星叫"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs[0].title").value("迷星叫"));
    }
}
