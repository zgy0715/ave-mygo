package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.avemygo.admin.dto.AdminEventDtos.TicketTypePatch;
import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSession;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.TicketType;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.order.OrderService;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 管理员持有旧快照时，购票提交后再编辑票档，不能覆盖刚发生的售票。 */
@SpringBootTest
@ActiveProfiles("test")
class AdminTicketConcurrencyTest {

    @Autowired private EventRepository events;
    @Autowired private LiveSessionRepository sessions;
    @Autowired private TicketTypeRepository tickets;
    @Autowired private OrderService orders;
    @Autowired private AdminEventService admin;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private Long eventId;
    private Long sessionId;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        LiveEvent event = new LiveEvent();
        event.setTitle("[TEST] 管理编辑与购票并发");
        event.setBandId((short) 1);
        event.setVenue("测试会场");
        event.setStatus("PUBLISHED");
        eventId = events.save(event).getId();

        LiveSession session = new LiveSession();
        session.setEventId(eventId);
        session.setName("测试场");
        session.setStartTime(LocalDateTime.now().plusDays(1));
        session.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        session.setSaleStartTime(LocalDateTime.now().minusDays(1));
        session.setStatus("ONSALE");
        sessionId = sessions.save(session).getId();

        TicketType ticket = new TicketType();
        ticket.setSessionId(sessionId);
        ticket.setName("普通席");
        ticket.setPrice(new BigDecimal("100.00"));
        ticket.setTotalStock(1);
        ticketId = tickets.save(ticket).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM ticket_order WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM user_session_quota WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM ticket_type WHERE id = ?", ticketId);
        jdbc.update("DELETE FROM live_session WHERE id = ?", sessionId);
        jdbc.update("DELETE FROM live_event WHERE id = ?", eventId);
    }

    @Test
    void editingPricePreservesConcurrentSaleAndOrderPriceSnapshot() throws Exception {
        editAfterSale(new TicketTypePatch(null, new BigDecimal("101.00"), null, null, null));

        var ticket = admin.sessions(eventId).get(0).ticketTypes().get(0);
        assertThat(ticket.soldCount()).isEqualTo(1);
        assertThat(ticket.remaining()).isZero();
        assertThat(ticket.price()).isEqualByComparingTo("101.00");
        assertThat(orders.myOrders(98765401L)).singleElement()
                .satisfies(order -> assertThat(order.unitPrice()).isEqualByComparingTo("100.00"));
        assertThatThrownBy(() -> orders.create(98765402L, new CreateOrderRequest(sessionId, ticketId, 1)))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(40901));
    }

    @Test
    void reducingStockBelowConcurrentSaleReturnsBusinessError() throws Exception {
        assertThatThrownBy(() -> editAfterSale(new TicketTypePatch(null, null, 0, null, null)))
                .hasCauseInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error.getCause()).getCode()).isEqualTo(40001));
        var ticket = admin.sessions(eventId).get(0).ticketTypes().get(0);
        assertThat(ticket.totalStock()).isEqualTo(1);
        assertThat(ticket.soldCount()).isEqualTo(1);
    }

    private void editAfterSale(TicketTypePatch patch) throws Exception {
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch sold = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> edit = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(tx -> {
                        // 模拟管理请求先读，购票后提交；保证交错顺序，无需靠 sleep 碰运气。
                        tickets.findById(ticketId).orElseThrow();
                        read.countDown();
                        try {
                            if (!sold.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("等待购票超时");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(error);
                        }
                        admin.updateTicketType(ticketId, patch);
                    }));
            assertThat(read.await(15, TimeUnit.SECONDS)).isTrue();
            orders.create(98765401L, new CreateOrderRequest(sessionId, ticketId, 1));
            sold.countDown();
            edit.get(20, TimeUnit.SECONDS);
        } finally {
            sold.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }
}
