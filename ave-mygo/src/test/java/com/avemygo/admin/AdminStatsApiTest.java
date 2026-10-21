package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.AuthService;
import com.avemygo.auth.JwtService;
import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.order.OrderService;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据看板：口径验证（只算 PAID、退款不计入）+ 趋势补零 + 上座率计算。
 * 关键点：先造 1 张已支付（¥1280×2）和 1 张退款单，断言营收与票数不含退款单。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStatsApiTest {

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

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generate(1L, "ADMIN");
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM ticket_type WHERE name LIKE '[ST]%'");
        jdbc.update("DELETE FROM live_session WHERE name LIKE '[ST]%'");
        jdbc.update("DELETE FROM live_event WHERE title LIKE '[ST]%'");

        buyerId = authService.register(new RegisterRequest("st_" + System.currentTimeMillis() % 1000000, "pass1234", null));
        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[ST] 统计演出',1,'统计场馆','PUBLISHED',0,NOW(),NOW())");
        eventId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'[ST] 统计场次',DATE_ADD(NOW(),INTERVAL 5 DAY),DATE_ADD(NOW(),INTERVAL 5 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),'ONSALE',0,NOW(),NOW())", eventId);
        sessionId = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
        jdbc.update("INSERT INTO ticket_type(session_id,name,price,total_stock,sold_count,max_per_user,status,created_at,updated_at) "
                + "VALUES(?,'[ST] S席',1280.00,10,0,4,1,NOW(),NOW())", sessionId);
        ticketTypeId = jdbc.queryForObject("SELECT MAX(id) FROM ticket_type", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM ticket_order");
        jdbc.update("DELETE FROM ticket_type WHERE name LIKE '[ST]%'");
        jdbc.update("DELETE FROM live_session WHERE name LIKE '[ST]%'");
        jdbc.update("DELETE FROM live_event WHERE title LIKE '[ST]%'");
        jdbc.update("DELETE FROM user WHERE username LIKE 'st_%'");
    }

    @Test
    @DisplayName("ST01 营收/票数只算 PAID：1 张已支付(2票) + 1 张退款 → 营收 2560、票数 2、退款数 1")
    void st01_revenueExcludesRefunded() throws Exception {
        var paid = orderService.create(buyerId, new CreateOrderRequest(sessionId, ticketTypeId, 2));
        orderService.pay(paid.orderNo(), buyerId);

        var toRefund = orderService.create(buyerId, new CreateOrderRequest(sessionId, ticketTypeId, 1));
        orderService.pay(toRefund.orderNo(), buyerId);
        // 走管理端退款接口，顺便验证与看板口径一致
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/orders/" + toRefund.orderNo() + "/refund")
                        .header("Authorization", adminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"统计口径测试\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/admin/stats/overview").param("days", "7").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.orders").value(1))
                .andExpect(jsonPath("$.data.totals.revenue").value(2560.00))
                .andExpect(jsonPath("$.data.totals.ticketsSold").value(2))
                .andExpect(jsonPath("$.data.totals.refundedOrders").value(1))
                .andExpect(jsonPath("$.data.daily.length()").value(7));
    }

    @Test
    @DisplayName("ST02 趋势补零：7 天请求返回 7 个点，无成交日期为 0")
    void st02_dailyFill() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/overview").param("days", "7").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.daily.length()").value(7))
                .andExpect(jsonPath("$.data.daily[6].orders").value(0))
                .andExpect(jsonPath("$.data.daily[6].revenue").value(0));

        mvc.perform(get("/api/v1/admin/stats/overview").param("days", "500").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.daily.length()").value(90));   // 上限保护
    }

    @Test
    @DisplayName("ST03 上座率：卖 2 张/库存 10 → 20.0%")
    void st03_occupancy() throws Exception {
        var created = orderService.create(buyerId, new CreateOrderRequest(sessionId, ticketTypeId, 2));
        orderService.pay(created.orderNo(), buyerId);

        mvc.perform(get("/api/v1/admin/stats/overview").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.sessions[?(@.sessionId == " + sessionId + ")].totalStock").value(10))
                .andExpect(jsonPath("$.data.sessions[?(@.sessionId == " + sessionId + ")].soldCount").value(2))
                .andExpect(jsonPath("$.data.sessions[?(@.sessionId == " + sessionId + ")].occupancy").value(20.0));
    }

    @Test
    @DisplayName("ST04 非管理员访问看板 → 403")
    void st04_forbidden() throws Exception {
        String userToken = "Bearer " + jwtService.generate(buyerId, "USER");
        mvc.perform(get("/api/v1/admin/stats/overview").header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }
}
