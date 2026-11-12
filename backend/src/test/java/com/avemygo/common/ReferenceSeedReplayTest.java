package com.avemygo.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

/** 完整回放冻结迁移与参考种子；只使用独立的随机测试库，避免污染现有库。 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceSeedReplayTest {

    @Autowired private DataSource dataSource;

    @Test
    void replayOnFreshMigrationsPreservesRuntimeIdentityAndTransactions() throws Exception {
        String database = "bandori_live_seed_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            String originalCatalog = connection.getCatalog();
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                }
                connection.setCatalog(database);
                var isolated = new SingleConnectionDataSource(connection, true);
                Flyway.configure().dataSource(isolated).defaultSchema(database)
                        .locations("classpath:db/migration").load().migrate();
                JdbcTemplate jdbc = new JdbcTemplate(isolated);

                var events = jdbc.queryForList("SELECT id, title, band_id FROM live_event ORDER BY id");
                var sessions = jdbc.queryForList("SELECT id, event_id, name FROM live_session ORDER BY id");
                var tickets = jdbc.queryForList("SELECT id, session_id, name FROM ticket_type ORDER BY id");
                jdbc.update("UPDATE ticket_type SET sold_count = 3, price = 111, total_stock = 77 WHERE id = 1");
                jdbc.update("UPDATE ticket_type SET is_deleted = 1 WHERE id = 2");
                jdbc.update("INSERT INTO ticket_order (order_no, user_id, event_id, session_id, ticket_type_id, quantity, unit_price, total_amount, pay_deadline) VALUES ('SEED_REPLAY_TEST', 9001, 1, 1, 1, 3, 111, 333, DATE_ADD(NOW(), INTERVAL 15 MINUTE))");
                jdbc.update("INSERT INTO user_session_quota (user_id, session_id, qty) VALUES (9001, 1, 3)");
                var activeTicket = jdbc.queryForMap("SELECT * FROM ticket_type WHERE id = 1");
                var order = jdbc.queryForList("SELECT * FROM ticket_order");
                var quota = jdbc.queryForList("SELECT * FROM user_session_quota");

                for (int replay = 0; replay < 2; replay++) {
                    ScriptUtils.executeSqlScript(connection, new EncodedResource(
                            new FileSystemResource("../db/seed/reference.sql"), StandardCharsets.UTF_8));
                    assertThat(jdbc.queryForList("SELECT id, title, band_id FROM live_event ORDER BY id")).isEqualTo(events);
                    assertThat(jdbc.queryForList("SELECT id, event_id, name FROM live_session ORDER BY id")).isEqualTo(sessions);
                    assertThat(jdbc.queryForList("SELECT id, session_id, name FROM ticket_type ORDER BY id")).isEqualTo(tickets);
                    assertThat(jdbc.queryForMap("SELECT * FROM ticket_type WHERE id = 1")).isEqualTo(activeTicket);
                    assertThat(jdbc.queryForObject("SELECT is_deleted FROM ticket_type WHERE id = 2", Integer.class)).isEqualTo(1);
                    assertThat(jdbc.queryForList("SELECT * FROM ticket_order")).isEqualTo(order);
                    assertThat(jdbc.queryForList("SELECT * FROM user_session_quota")).isEqualTo(quota);
                }

                // 补齐缺失票档时，不能复制开发库里的已售数或主键。
                jdbc.update("DELETE FROM ticket_type WHERE id = 5");
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new FileSystemResource("../db/seed/reference.sql"), StandardCharsets.UTF_8));
                assertThat(jdbc.queryForObject("SELECT sold_count FROM ticket_type WHERE session_id = 3 AND name = 'S席（特製グッズ付）'", Integer.class)).isZero();

                // 整个演出缺失时，场次和票档必须跟随目标库生成的新主键。
                var missingEvent = jdbc.queryForMap("SELECT title, band_id FROM live_event WHERE id = 6");
                var missingSessions = jdbc.queryForList("SELECT name FROM live_session WHERE event_id = 6 ORDER BY name");
                var missingTickets = jdbc.queryForList("SELECT s.name AS session_name, t.name FROM ticket_type t JOIN live_session s ON s.id = t.session_id WHERE s.event_id = 6 ORDER BY s.name, t.name");
                jdbc.update("DELETE i FROM setlist_item i JOIN live_session s ON s.id = i.session_id WHERE s.event_id = 6");
                jdbc.update("DELETE t FROM ticket_type t JOIN live_session s ON s.id = t.session_id WHERE s.event_id = 6");
                jdbc.update("DELETE FROM live_session WHERE event_id = 6");
                jdbc.update("DELETE FROM live_event WHERE id = 6");
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new FileSystemResource("../db/seed/reference.sql"), StandardCharsets.UTF_8));
                Long newEventId = jdbc.queryForObject("SELECT id FROM live_event WHERE title = ? AND band_id <=> ?",
                        Long.class, missingEvent.get("title"), missingEvent.get("band_id"));
                assertThat(newEventId).isNotEqualTo(6L);
                assertThat(jdbc.queryForList("SELECT name FROM live_session WHERE event_id = ? ORDER BY name", newEventId))
                        .isEqualTo(missingSessions);
                assertThat(jdbc.queryForList("SELECT s.name AS session_name, t.name FROM ticket_type t JOIN live_session s ON s.id = t.session_id WHERE s.event_id = ? ORDER BY s.name, t.name", newEventId))
                        .isEqualTo(missingTickets);
            } finally {
                connection.setCatalog(originalCatalog);
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
                }
            }
        }
    }
}
