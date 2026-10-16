package com.avemygo.auth;

import com.avemygo.common.ApiResponse;
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
}
