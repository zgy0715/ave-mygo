package com.avemygo.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.avemygo.auth.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 权限边界测试（docs/08 T07）：
 * 未登录访问受保护接口 → 401；普通用户访问管理端 → 403；ADMIN 放行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;

    @Test
    @DisplayName("未登录访问 /orders/my → 401")
    void anonymousOrders() throws Exception {
        mvc.perform(get("/api/v1/orders/my")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通用户访问 /admin/songs → 403")
    void userCannotAccessAdmin() throws Exception {
        String token = jwtService.generate(9999L, "USER");
        mvc.perform(get("/api/v1/admin/songs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员访问 /admin/songs → 200")
    void adminCanAccessAdmin() throws Exception {
        String token = jwtService.generate(1L, "ADMIN");
        mvc.perform(get("/api/v1/admin/songs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("公开接口（歌曲列表）无需登录 → 200")
    void publicSongs() throws Exception {
        mvc.perform(get("/api/v1/songs")).andExpect(status().isOk());
    }
}
