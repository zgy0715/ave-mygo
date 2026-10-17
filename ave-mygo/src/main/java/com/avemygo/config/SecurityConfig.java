package com.avemygo.config;

import com.avemygo.auth.JwtAuthFilter;
import com.avemygo.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（M2 收紧版）。
 * 规则：auth/** 与 health 公开；admin/** 需 ADMIN 角色；其余必须登录。
 * JWT 无状态：完全不创建 session。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 密码加密器：BCrypt 单向哈希 + 随机盐，存密码的唯一正确姿势 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)                 // 前后端分离 + token，无 CSRF 风险
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 公开区：认证 + 健康检查 + 内容浏览（游客可看，登录才能操作）
                        .requestMatchers("/api/v1/auth/**", "/api/v1/health",
                                "/api/v1/songs/**", "/api/v1/albums/**",
                                "/api/v1/events/**", "/api/v1/setlists/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()                  // 其余：必须登录
                )
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    // 未盖章的请求访问受保护路径：401 + 统一错误码
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getOutputStream().write(
                            "{\"code\":40101,\"message\":\"未登录或登录已过期\",\"data\":null}"
                                    .getBytes());
                }))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
