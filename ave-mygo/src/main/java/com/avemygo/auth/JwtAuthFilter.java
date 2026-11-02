package com.avemygo.auth;

import com.avemygo.common.ApiResponse;
import com.avemygo.user.User;
import com.avemygo.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 过滤器：每个请求进入 Controller 前经过这里。
 * 职责：有 token 就验真、盖章（写入 SecurityContext）；没 token 就放行给后续规则判断。
 * 注意验签失败直接回 401 结束请求——假通行证没有继续旅行的资格。
 *
 * 不加 @Component：由 SecurityConfig 手动构造并插入 Security 过滤链，
 * 避免被 Servlet 容器重复注册导致每个请求执行两次。
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // 没有 Bearer 头：不盖章直接放行（公开路径正常访问；受保护路径会被规则拦下）
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            // 账户状态与令牌版本校验（V55）：封禁/改权限后旧令牌立刻失效，
            // 不再出现"封了人还能用 24 小时"的窗口。
            //
            // 只校验带 ver 声明的令牌：升级前签发的老令牌（无 ver）允许自然过期，
            // 这是灰度过渡——上线后新签发的令牌都带版本，24 小时内老令牌自然消失。
            Integer tokenVersion = claims.get("ver", Integer.class);
            if (tokenVersion != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || user.getStatus() == null || user.getStatus() == 0) {
                    reject(response, "账号已被停用，请联系管理员");
                    return;
                }
                int dbVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
                if (tokenVersion != dbVersion) {
                    reject(response, "登录状态已失效，请重新登录");
                    return;
                }
            }

            // 盖章：Spring Security 只认 Authentication 对象；权限名约定前缀 ROLE_
            var auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            // token 伪造/过期/格式错误：401 + 统一错误码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"code\":40101,\"message\":\"未登录或登录已过期\",\"data\":null}"
                            .getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 统一的 401 拒绝（不区分"token 无效"与"账号停用"的具体原因给前端时也够用） */
    private void reject(HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                ("{\"code\":40101,\"message\":\"" + message + "\",\"data\":null}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
