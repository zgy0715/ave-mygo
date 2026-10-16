package com.avemygo.auth;

import com.avemygo.auth.dto.LoginRequest;
import com.avemygo.auth.dto.LoginResponse;
import com.avemygo.auth.dto.RegisterRequest;
import com.avemygo.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。门卫角色：收参数（@Valid 校验）→ 调 Service → 包统一响应。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        Long userId = authService.register(req);
        return ApiResponse.ok(Map.of(
                "userId", userId,
                "username", req.username()
        ));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    /** 身份验证演示接口：能带有效 token 访问到这，说明 JWT 链路通 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication authentication) {
        return ApiResponse.ok(Map.of(
                "userId", authentication.getName(),
                "authorities", authentication.getAuthorities().toString()
        ));
    }
}
