package com.avemygo.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.avemygo.event.LiveSessionRepository;
import com.avemygo.song.AlbumRepository;
import com.avemygo.song.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 软删除过滤回归：Album / Member / LiveSession 曾缺 @SQLRestriction，
 * 一旦做软删除，被删数据仍会出现在查询里（Song / LiveEvent 早已有过滤）。
 */
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterTest {

    @Autowired private AlbumRepository albumRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LiveSessionRepository sessionRepository;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM live_session WHERE name LIKE '[TEST]%'");
        jdbc.update("DELETE FROM member WHERE name_ja LIKE '[TEST]%'");
        jdbc.update("DELETE FROM album WHERE name LIKE '[TEST]%'");
        jdbc.update("DELETE FROM live_event WHERE title LIKE '[TEST]%'");
    }

    @Test
    @DisplayName("专辑软删除后不出现在查询结果中")
    void albumFiltered() {
        jdbc.update("INSERT INTO album(band_id,name,type,is_deleted,created_at,updated_at) "
                + "VALUES(1,'[TEST] 已删专辑','SINGLE',1,NOW(),NOW())");
        jdbc.update("INSERT INTO album(band_id,name,type,is_deleted,created_at,updated_at) "
                + "VALUES(1,'[TEST] 正常专辑','SINGLE',0,NOW(),NOW())");

        var names = albumRepository.findAll().stream().map(a -> a.getName()).toList();
        assertThat(names).contains("[TEST] 正常专辑").doesNotContain("[TEST] 已删专辑");
    }

    @Test
    @DisplayName("成员软删除后不出现在查询结果中")
    void memberFiltered() {
        jdbc.update("INSERT INTO member(band_id,name_ja,name_romaji,cv,position,is_deleted,created_at,updated_at) "
                + "VALUES(1,'[TEST] 已删成员','TEST','CV','Vo.',1,NOW(),NOW())");
        var names = memberRepository.findAll().stream().map(m -> m.getNameJa()).toList();
        assertThat(names).doesNotContain("[TEST] 已删成员");
    }

    @Test
    @DisplayName("场次软删除后不出现在演出详情查询中")
    void sessionFiltered() {
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[TEST] 软删场次演出',1,'场馆','PUBLISHED',0,NOW(),NOW())");
        Long eventId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'[TEST] 已删场次',NOW(),NOW(),NOW(),'ONSALE',1,NOW(),NOW())", eventId);

        assertThat(sessionRepository.findByEventIdOrderByStartTimeAsc(eventId)).isEmpty();
    }
}
