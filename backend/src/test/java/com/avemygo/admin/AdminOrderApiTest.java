package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.AuthService;
import com.avemygo.auth.JwtService;
import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.order.OrderService;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

/**
 * 管理端订单：检索（含关联名称）+ 退款（状态机 + 库存回滚）+ 参数校验。
 * 这是 docs/04 管理端 P0 的最后一块，退款必须证明 sold_count 真的降回去。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOrderApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private OrderService orderService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private Long eventId;
    private Long sessionId;
    private Long ticketTypeId;
    private Long buyerId;
    private String buyerName;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        cleanup();
        buyerName = "ord_" + System.currentTimeMillis() % 1000000;
        buyerId = authService.register(new RegisterRequest(buyerName, "pass1234", null));

        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[TEST] 订单管理演出',1,'测试场馆','PUBLISHED',0,NOW(),NOW())");
        eventId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'昼场',DATE_ADD(NOW(),INTERVAL 20 DAY),DATE_ADD(NOW(),INTERVAL 20 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),'ONSALE',0,NOW(),NOW())", eventId);
        sessionId = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
        jdbc.update("INSERT INTO ticket_type(session_id,name,price,total_stock,sold_count,max_per_user,status,created_at,updated_at) "
                + "VALUES(?,'S席',1000.00,10,0,4,1,NOW(),NOW())", sessionId);
        ticketTypeId = jdbc.queryForObject("SELECT MAX(id) FROM ticket_type", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM ticket_type");
        jdbc.update("DELETE FROM live_session");
        jdbc.update("DELETE FROM live_event");
        jdbc.update("DELETE FROM user WHERE username LIKE 'ord_%'");
    }

    private String createPaidOrder() {
        var created = orderService.create(buyerId,
                new CreateOrderRequest(sessionId, ticketTypeId, 2));
        orderService.pay(created.orderNo(), buyerId);
        return created.orderNo();
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT sold_count FROM ticket_type WHERE id=?", Integer.class, ticketTypeId);
    }

    @Test
    @DisplayName("O01 订单检索：返回用户名/演出/场次/票档名称，可按状态过滤")
    void o01_search() throws Exception {
        createPaidOrder();

        mvc.perform(get("/api/v1/admin/orders").param("status", "PAID").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].username").value(buyerName))
                .andExpect(jsonPath("$.data.content[0].eventTitle").value("[TEST] 订单管理演出"))
                .andExpect(jsonPath("$.data.content[0].sessionName").value("昼场"))
                .andExpect(jsonPath("$.data.content[0].ticketTypeName").value("S席"))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(2000.00));
    }

    @Test
    @DisplayName("O02 退款：状态 PAID→REFUNDED，库存回滚 2 张，原因落库")
    void o02_refund() throws Exception {
        String orderNo = createPaidOrder();
        assertThat(soldCount()).isEqualTo(2);

        mvc.perform(patch("/api/v1/admin/orders/" + orderNo + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"用户申请退票\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundReason").value("用户申请退票"));

        assertThat(soldCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM ticket_order WHERE order_no=?", String.class, orderNo))
                .isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("O03 重复退款 → 40902，库存不会被扣成负数")
    void o03_doubleRefund() throws Exception {
        String orderNo = createPaidOrder();
        mvc.perform(patch("/api/v1/admin/orders/" + orderNo + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第一次退款\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(patch("/api/v1/admin/orders/" + orderNo + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第二次退款\"}"))
                .andExpect(jsonPath("$.code").value(40902));
        assertThat(soldCount()).isZero();
    }

    @Test
    @DisplayName("O04 待支付订单不可退 → 40902；空原因 → 40001")
    void o04_guards() throws Exception {
        var created = orderService.create(buyerId, new CreateOrderRequest(sessionId, ticketTypeId, 1));
        mvc.perform(patch("/api/v1/admin/orders/" + created.orderNo() + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"还没付款也退\"}"))
                .andExpect(jsonPath("$.code").value(40902));

        String paid = createPaidOrder();
        mvc.perform(patch("/api/v1/admin/orders/" + paid + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(jsonPath("$.code").value(40001));
    }
}
