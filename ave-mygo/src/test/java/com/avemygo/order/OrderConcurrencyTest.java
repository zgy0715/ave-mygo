package com.avemygo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.avemygo.common.BizException;
import com.avemygo.event.EventRepository;
import com.avemygo.event.LiveEvent;
import com.avemygo.event.LiveSession;
import com.avemygo.event.LiveSessionRepository;
import com.avemygo.event.TicketType;
import com.avemygo.event.TicketTypeRepository;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import com.avemygo.order.dto.OrderDtos.OrderCreated;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 购票核心链路集成测试（真 MySQL 测试库 bandori_live_test，无 Docker）。
 * 覆盖 docs/08 P0 用例：T01 正常下单 / T02 并发超卖 / T03 超限购 / T04 库存在库扣减 / T05 CHECK 兜底 / T06 超时取消。
 * 注意：本类刻意不加 @Transactional —— 要让每笔下单真实提交，才能验证并发与约束。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

    private static final BigDecimal PRICE = new BigDecimal("1000.00");

    @Autowired private OrderService orderService;
    @Autowired private EventRepository eventRepository;
    @Autowired private LiveSessionRepository sessionRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private TicketOrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long eventId;
    private Long sessionId;
    private Long ticketTypeId;

    /** 建一套可售演出/场次/票档 */
    private void fixture(int totalStock, int maxPerUser) {
        LiveEvent event = new LiveEvent();
        event.setTitle("[TEST] 并发测试 Live");
        event.setBandId((short) 1);
        event.setVenue("测试场馆");
        event.setStatus("PUBLISHED");
        eventId = eventRepository.save(event).getId();

        LiveSession session = new LiveSession();
        session.setEventId(eventId);
        session.setName("昼场");
        session.setStartTime(LocalDateTime.now().plusDays(30));
        session.setEndTime(LocalDateTime.now().plusDays(30).plusHours(3));
        session.setSaleStartTime(LocalDateTime.now().minusDays(1));
        session.setStatus("ONSALE");
        sessionId = sessionRepository.save(session).getId();

        TicketType type = new TicketType();
        type.setSessionId(sessionId);
        type.setName("S席");
        type.setPrice(PRICE);
        type.setTotalStock(totalStock);
        type.setMaxPerUser((byte) maxPerUser);
        type.setStatus((byte) 1);
        ticketTypeId = ticketTypeRepository.save(type).getId();
    }

    @BeforeEach
    void setUp() {
        // 每个用例自建数据，避免测试库历史残留干扰
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        // 逆依赖顺序清库（测试专用表，jdbc 直删避免软删除过滤）
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM ticket_type");
        jdbc.update("DELETE FROM live_session");
        jdbc.update("DELETE FROM live_event");
    }

    private OrderCreated buy(long userId, int qty) {
        return orderService.create(userId, new CreateOrderRequest(sessionId, ticketTypeId, qty));
    }

    private int soldCount() {
        return jdbc.queryForObject(
                "SELECT sold_count FROM ticket_type WHERE id = " + ticketTypeId, Integer.class);
    }

    @Test
    @DisplayName("T01 正常下单：余票 50 买 2 → 订单 PENDING_PAYMENT、sold_count=2、金额=单价×2")
    void t01_normalOrder() {
        fixture(50, 4);
        OrderCreated created = buy(9001L, 2);

        assertThat(created.orderNo()).startsWith("BL");
        assertThat(created.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(created.quantity()).isEqualTo(2);
        assertThat(created.totalAmount()).isEqualByComparingTo("2000.00");
        assertThat(created.payDeadline()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("T02 并发超卖：余票 1，8 线程各买 1 → 恰好 1 单成功，sold_count=1")
    void t02_concurrentOversell() throws Exception {
        fixture(1, 10);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger stockShortage = new AtomicInteger();
        List<String> unexpected = new ArrayList<>();

        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long userId = 9100L + i;
            jobs.add(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    buy(userId, 1);
                    success.incrementAndGet();
                } catch (BizException e) {
                    if (e.getCode() == 40901) {
                        stockShortage.incrementAndGet();
                    } else {
                        unexpected.add(e.getCode() + ":" + e.getMessage());
                    }
                } catch (Exception e) {
                    unexpected.add(e.getClass().getSimpleName() + ":" + e.getMessage());
                }
                return null;
            });
        }
        List<Future<Void>> futures = new ArrayList<>();
        jobs.forEach(j -> futures.add(pool.submit(j)));
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();                       // 同时放闸，制造真实争抢
        for (Future<Void> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected).as("不应出现库存不足之外的异常").isEmpty();
        assertThat(success.get()).as("只能有一单抢到").isEqualTo(1);
        assertThat(stockShortage.get()).isEqualTo(threads - 1);
        assertThat(soldCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("T03 超限购：max_per_user=4，已购 3 再买 2 → 40903，库存不变")
    void t03_overLimit() {
        fixture(50, 4);
        buy(9200L, 3);

        assertThatThrownBy(() -> buy(9200L, 2))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40903);
        assertThat(soldCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("T04 库存快照：下单 2 张后从库中直接读取 sold_count=2")
    void t04_stockSnapshot() {
        fixture(50, 4);
        buy(9300L, 2);
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("T05 CHECK 兜底：SQL 强行把 sold_count 改超 total_stock → 数据库拒绝")
    void t05_checkConstraint() {
        fixture(5, 4);
        // MySQL 3819 错误码 Spring 归为 UncategorizedSQLException，断言其语义：库层拒绝且点名 chk_stock
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE ticket_type SET sold_count = total_stock + 1 WHERE id = " + ticketTypeId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_stock");
        assertThat(soldCount()).isZero();
    }

    @Test
    @DisplayName("T06 超时取消：pay_deadline 过期 → 状态 EXPIRED 且库存回滚")
    void t06_expireRollback() {
        fixture(10, 4);
        OrderCreated created = buy(9400L, 2);
        assertThat(soldCount()).isEqualTo(2);

        jdbc.update("UPDATE ticket_order SET pay_deadline = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE order_no = ?",
                created.orderNo());
        orderService.expirePendingOrders();

        String status = jdbc.queryForObject(
                "SELECT status FROM ticket_order WHERE order_no = ?", String.class, created.orderNo());
        assertThat(status).isEqualTo("EXPIRED");
        assertThat(soldCount()).isZero();
    }

    @Test
    @DisplayName("T08 草稿演出不可购票：DRAFT 演出的 ONSALE 场次下单 → 40902")
    void t08_draftEventBlocked() {
        // 手动构造：演出 DRAFT + 场次 ONSALE（后台允许这种组合，必须由下单接口兜住）
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[TEST] 草稿演出',1,'测试场馆','DRAFT',0,NOW(),NOW())");
        eventId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                        + "VALUES(?,'昼场',DATE_ADD(NOW(), INTERVAL 30 DAY),DATE_ADD(NOW(), INTERVAL 30 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY),'ONSALE',0,NOW(),NOW())",
                eventId);
        sessionId = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
        jdbc.update("INSERT INTO ticket_type(session_id,name,price,total_stock,sold_count,max_per_user,status,created_at,updated_at) "
                + "VALUES(?,'S席',1000.00,10,0,4,1,NOW(),NOW())", sessionId);
        ticketTypeId = jdbc.queryForObject("SELECT MAX(id) FROM ticket_type", Long.class);

        assertThatThrownBy(() -> buy(9600L, 1))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40902);
        assertThat(soldCount()).isZero();
    }

    @Test
    @DisplayName("T07 支付幂等：已支付订单再次支付 → 直接返回 PAID，不重复扣款")
    void t07_payIdempotent() {
        fixture(10, 4);
        OrderCreated created = buy(9500L, 1);

        var first = orderService.pay(created.orderNo(), 9500L);
        var second = orderService.pay(created.orderNo(), 9500L);

        assertThat(first.status()).isEqualTo("PAID");
        assertThat(second.status()).isEqualTo("PAID");
        assertThat(soldCount()).isEqualTo(1);
    }
}
