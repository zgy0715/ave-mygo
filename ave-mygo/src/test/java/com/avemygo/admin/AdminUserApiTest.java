package com.avemygo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.AuthService;
import com.avemygo.auth.JwtService;
import com.avemygo.auth.dto.LoginRequest;
import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.common.BizException;
import com.avemygo.order.OrderService;
import com.avemygo.order.dto.OrderDtos.CreateOrderRequest;
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
 * 管理端用户：检索（含消费统计）+ 封禁/解封 + 防锁死规则。
 * 重点验证"封禁 → 登录被拒"这条闭环，否则后台按钮等于装饰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AuthService authService;
    @Autowired private OrderService orderService;
    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private Long adminId;
    private Long targetId;
    private String targetName;
    private Long eventId;
    private Long sessionId;
    private Long ticketTypeId;

    @BeforeEach
    void setUp() {
        cleanup();
        // 管理员账号（真实入库，便于"不能封自己"用例）
        adminId = authService.register(new RegisterRequest("au_admin", "pass1234", "管理员"));
        jdbc.update("UPDATE user SET role='ADMIN' WHERE id=?", adminId);
        adminToken = "Bearer " + jwtService.generate(adminId, "ADMIN");

        targetName = "au_user_" + System.currentTimeMillis() % 100000;
        targetId = authService.register(new RegisterRequest(targetName, "pass1234", "被封用户"));

        jdbc.update("INSERT INTO live_event(title,band_id,venue,status,is_deleted,created_at,updated_at) "
                + "VALUES('[AU] 演出',1,'场馆','PUBLISHED',0,NOW(),NOW())");
        eventId = jdbc.queryForObject("SELECT MAX(id) FROM live_event", Long.class);
        jdbc.update("INSERT INTO live_session(event_id,name,start_time,end_time,sale_start_time,status,is_deleted,created_at,updated_at) "
                + "VALUES(?,'[AU] 场次',DATE_ADD(NOW(),INTERVAL 3 DAY),DATE_ADD(NOW(),INTERVAL 3 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),'ONSALE',0,NOW(),NOW())", eventId);
        sessionId = jdbc.queryForObject("SELECT MAX(id) FROM live_session", Long.class);
        jdbc.update("INSERT INTO ticket_type(session_id,name,price,total_stock,sold_count,max_per_user,status,created_at,updated_at) "
                + "VALUES(?,'[AU] 票档',500.00,10,0,4,1,NOW(),NOW())", sessionId);
        ticketTypeId = jdbc.queryForObject("SELECT MAX(id) FROM ticket_type", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM ticket_order WHERE event_id IN (SELECT id FROM live_event WHERE title='[AU] 演出')");
        jdbc.update("DELETE FROM ticket_type WHERE name='[AU] 票档'");
        jdbc.update("DELETE FROM live_session WHERE name='[AU] 场次'");
        jdbc.update("DELETE FROM live_event WHERE title='[AU] 演出'");
        jdbc.update("DELETE FROM user WHERE username LIKE 'au\\_%'");
    }

    @Test
    @DisplayName("U01 列表：关键词命中 + 消费统计（已支付订单计入，未付不计）")
    void u01_list() throws Exception {
        var paid = orderService.create(targetId, new CreateOrderRequest(sessionId, ticketTypeId, 2));
        orderService.pay(paid.orderNo(), targetId);
        orderService.create(targetId, new CreateOrderRequest(sessionId, ticketTypeId, 1));   // 未支付

        mvc.perform(get("/api/v1/admin/users").param("keyword", targetName)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].username").value(targetName))
                .andExpect(jsonPath("$.data.content[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.content[0].totalSpent").value(1000.00));
    }

    @Test
    @DisplayName("U02 封禁 → 登录被拒 40301；解封 → 可正常登录")
    void u02_banAndUnban() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/" + targetId + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(0));

        try {
            authService.login(new LoginRequest(targetName, "pass1234"));
            org.junit.jupiter.api.Assertions.fail("封禁用户不应能登录");
        } catch (BizException e) {
            assertThat(e.getCode()).isEqualTo(40301);
        }

        mvc.perform(patch("/api/v1/admin/users/" + targetId + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
                .andExpect(jsonPath("$.data.status").value(1));
        assertThat(authService.login(new LoginRequest(targetName, "pass1234")).token()).isNotBlank();
    }

    @Test
    @DisplayName("U03 不能封自己 → 40001，账号状态不变")
    void u03_cannotBanSelf() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/" + adminId + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(40001));
        assertThat(jdbc.queryForObject("SELECT status FROM user WHERE id=?", Byte.class, adminId)).isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("U04 不能封禁其他管理员；目标不存在 40401；非法状态 40001")
    void u04_guards() throws Exception {
        Long anotherAdmin = authService.register(new RegisterRequest("au_admin2", "pass1234", "二号管理员"));
        jdbc.update("UPDATE user SET role='ADMIN' WHERE id=?", anotherAdmin);

        mvc.perform(patch("/api/v1/admin/users/" + anotherAdmin + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(40001));

        mvc.perform(patch("/api/v1/admin/users/999999/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(40401));

        mvc.perform(patch("/api/v1/admin/users/" + targetId + "/status").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":5}"))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("U05 普通用户访问用户管理 → 403")
    void u05_forbidden() throws Exception {
        String userToken = "Bearer " + jwtService.generate(targetId, "USER");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }
}
