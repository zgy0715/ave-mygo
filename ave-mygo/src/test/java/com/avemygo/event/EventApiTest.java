package com.avemygo.event;

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
 * 演出接口：草稿隔离、详情字段、票档余票派生、setlist 读取（含安可顺序）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private Long publishedId;
    private Long draftId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[EV] 已发布演出',1,'公开场馆','PUBLISHED',0,NOW(),NOW())");
        publishedId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[EV] 草稿演出',1,'秘密场馆','DRAFT',0,NOW(),NOW())");
        draftId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);

        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'[EV] 昼场',DATE_ADD(NOW(),INTERVAL 5 DAY),DATE_ADD(NOW(),INTERVAL 5 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),'ONSALE',0,NOW(),NOW())", publishedId);
        sessionId = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
        jdbc.update("INSERT INTO ticket_type(session_id,name,price,total_stock,sold_count,max_per_user,status,created_at,updated_at) "
                + "VALUES(?,'[EV] S席',1280.00,10,3,4,1,NOW(),NOW())", sessionId);
        jdbc.update("INSERT INTO ticket_type(session_id,name,price,total_stock,sold_count,max_per_user,status,created_at,updated_at) "
                + "VALUES(?,'[EV] 停售票',500.00,10,0,4,0,NOW(),NOW())", sessionId);

        // setlist：2 首 + 1 首安可
        jdbc.update("INSERT INTO setlist_item(session_id,song_id,order_no,is_encore) VALUES(?,1,1,0)", sessionId);
        jdbc.update("INSERT INTO setlist_item(session_id,song_id,order_no,is_encore) VALUES(?,2,2,0)", sessionId);
        jdbc.update("INSERT INTO setlist_item(session_id,song_id,order_no,is_encore) VALUES(?,3,3,1)", sessionId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM setlist_item WHERE session_id IN (SELECT id FROM live_session WHERE name LIKE '[EV]%')");
        jdbc.update("DELETE FROM ticket_type WHERE name LIKE '[EV]%'");
        jdbc.update("DELETE FROM live_session WHERE name LIKE '[EV]%'");
        jdbc.update("DELETE FROM live_event WHERE title LIKE '[EV]%'");
    }

    @Test
    @DisplayName("E01 列表不含草稿演出")
    void e01_draftHidden() throws Exception {
        mvc.perform(get("/api/v1/events").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '[EV] 草稿演出')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.title == '[EV] 已发布演出')]").isNotEmpty());
    }

    @Test
    @DisplayName("E02 草稿详情按不存在处理（40401）")
    void e02_draftDetail() throws Exception {
        mvc.perform(get("/api/v1/events/" + draftId))
                .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(get("/api/v1/events/" + publishedId))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.venue").value("公开场馆"));
    }

    @Test
    @DisplayName("E03 详情票档：只给在售票档，余票 = 库存 - 已售")
    void e03_ticketTypes() throws Exception {
        mvc.perform(get("/api/v1/events/" + publishedId))
                .andExpect(jsonPath("$.data.sessions.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].ticketTypes.length()").value(1))
                .andExpect(jsonPath("$.data.sessions[0].ticketTypes[0].name").value("[EV] S席"))
                .andExpect(jsonPath("$.data.sessions[0].ticketTypes[0].availableStock").value(7))
                .andExpect(jsonPath("$.data.sessions[0].ticketTypes[0].totalStock").value(10));
    }

    @Test
    @DisplayName("E04 setlist：按序号返回，安可标记正确，标题非空")
    void e04_setlist() throws Exception {
        mvc.perform(get("/api/v1/events/sessions/" + sessionId + "/setlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].orderNo").value(1))
                .andExpect(jsonPath("$.data[2].encore").value(true))
                .andExpect(jsonPath("$.data[0].songTitle").isNotEmpty());
    }

    @Test
    @DisplayName("E05 无歌单的场次返回空数组（不报错）")
    void e05_emptySetlist() throws Exception {
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'[EV] 空场',NOW(),NOW(),NOW(),'NOT_OPEN',0,NOW(),NOW())", publishedId);
        Long emptySession = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
        mvc.perform(get("/api/v1/events/sessions/" + emptySession + "/setlist"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
