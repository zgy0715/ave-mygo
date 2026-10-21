package com.avemygo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.common.BizException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 注册并发重名：exists 查询与唯一索引之间存在竞态窗口，
 * 必须恰好一个成功、另一个拿到 40904（而不是 50001 系统异常）。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRegisterConcurrencyTest {

    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    private final String username = "race_" + System.currentTimeMillis() % 100000;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM user WHERE username LIKE 'race_%'");
    }

    @Test
    @DisplayName("同一用户名并发注册两次 → 1 成功 + 1 个 40904，库里只有 1 行")
    void concurrentRegister() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    authService.register(new RegisterRequest(username, "pass1234", null));
                    ok.incrementAndGet();
                } catch (BizException e) {
                    if (e.getCode() == 40904) duplicate.incrementAndGet();
                    else unexpected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(unexpected.get()).as("不应出现 40904 之外的异常").isZero();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(duplicate.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user WHERE username = ?", Integer.class, username)).isEqualTo(1);
    }

    @Test
    @DisplayName("串行重复注册 → 40904")
    void sequentialDuplicate() {
        authService.register(new RegisterRequest(username, "pass1234", null));
        try {
            authService.register(new RegisterRequest(username, "pass1234", null));
            org.junit.jupiter.api.Assertions.fail("应当抛出 40904");
        } catch (BizException e) {
            assertThat(e.getCode()).isEqualTo(40904);
        }
    }
}
