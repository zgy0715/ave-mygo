package com.avemygo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置。
 * M1 阶段全部放行，保证端到端链路先通；M2 用户体系时改为：
 * 放行 /api/v1/auth/**、/api/v1/health，其余要求认证，/api/v1/admin/** 要求 ADMIN 角色。
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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)   // 前后端分离 + JWT，无 session，无需 CSRF
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()        // TODO(M2): 收紧为 authenticated()
                );
        return http.build();
    }
}
