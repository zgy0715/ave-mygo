package com.avemygo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 轻量 IP 限流（固定窗口计数），上线前的最小防护。
 *
 * 分组阈值（每分钟，按 IP 计数；登录类更严，避免撞库）：
 *  - /api/v1/auth/login|register : 10
 *  - /api/v1/orders（下单）        : 20
 *  - /api/v1/assistant/**          : 20
 *  - 其他                          : 120
 *
 * 设计取舍：单机内存计数，不引 Redis——本项目单实例部署足够；
 * 若将来多实例，需要换成 Redis 令牌桶（接口已隔离，替换成本低）。
 * 测试 profile 默认关闭（app.rate-limit.enabled=false），避免用例互相污染。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // 在 RequestIdFilter 之后，日志里能带上 requestId
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_KEYS = 10_000;   // 防内存无限增长

    private final boolean enabled;
    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    /** 各分组每分钟额度（可配置：本地跑 E2E 时要放宽，否则 20 次登录就打满） */
    private final int authLimit;
    private final int orderLimit;
    private final int assistantLimit;
    private final int defaultLimit;

    /** CORS 白名单：429 也要带跨域头，否则浏览器只会报"CORS 错误"，真因被掩盖 */
    private final List<String> allowedOrigins;

    /**
     * 可信反向代理地址：只有请求来自这些地址时，才采信 X-Forwarded-For。
     * 否则任何人自带一个 XFF 头就能伪装成不同 IP，绕过限流（审阅项 XFF）。
     */
    private final List<String> trustedProxies;

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.auth:10}") int authLimit,
                           @Value("${app.rate-limit.order:20}") int orderLimit,
                           @Value("${app.rate-limit.assistant:20}") int assistantLimit,
                           @Value("${app.rate-limit.default-limit:120}") int defaultLimit,
                           @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins,
                           @Value("${app.rate-limit.trusted-proxies:127.0.0.1,0:0:0:0:0:0:0:1,::1}")
                           String trustedProxies) {
        this.enabled = enabled;
        this.authLimit = authLimit;
        this.orderLimit = orderLimit;
        this.assistantLimit = assistantLimit;
        this.defaultLimit = defaultLimit;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
        this.trustedProxies = java.util.Arrays.stream(trustedProxies.split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private record Window(long startMs, AtomicInteger count) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        String key = clientIp(request) + "|" + group(path);
        int limit = limitOf(path);

        if (!allow(key, limit)) {
            log.warn("限流命中 ip={} path={} limit={}/min", clientIp(request), path, limit);
            // 关键：429 必须带跨域头，否则浏览器把它显示成 CORS 错误，排查会完全跑偏
            String origin = request.getHeader("Origin");
            if (origin != null && allowedOrigins.contains(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Vary", "Origin");
            }
            response.setHeader("Retry-After", "60");
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"code\":42901,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        if (buckets.size() > MAX_KEYS) {
            buckets.entrySet().removeIf(e -> now - e.getValue().startMs() > WINDOW_MS);
        }
        Window window = buckets.compute(key, (k, old) -> {
            if (old == null || now - old.startMs() >= WINDOW_MS) {
                return new Window(now, new AtomicInteger(1));
            }
            old.count().incrementAndGet();
            return old;
        });
        return window.count().get() <= limit;
    }

    private String group(String path) {
        if (path.startsWith("/api/v1/auth/")) return "auth";
        if (path.startsWith("/api/v1/orders")) return "order";
        if (path.startsWith("/api/v1/assistant")) return "assistant";
        return "default";
    }

    private int limitOf(String path) {
        return switch (group(path)) {
            case "auth" -> authLimit;
            case "order" -> orderLimit;
            case "assistant" -> assistantLimit;
            default -> defaultLimit;
        };
    }

    /**
     * 取限流身份用的客户端 IP。
     *
     * 安全前提（审阅项）：**只有当请求来自可信代理时才采信 X-Forwarded-For**。
     * 否则客户端自己带一个 XFF 头就能伪装成任意 IP，把限流绕成摆设。
     * 配合部署要求：反向代理必须用 proxy_set_header X-Forwarded-For $remote_addr
     * 「覆盖」而不是追加，这样首段才是真实来源。
     */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) {
            return remote;                     // 直连或不可信来源：只认 TCP 层地址
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return remote;
    }
}
