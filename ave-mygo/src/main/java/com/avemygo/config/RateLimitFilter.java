package com.avemygo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
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
            case "auth" -> 10;
            case "order" -> 20;
            case "assistant" -> 20;
            default -> 120;
        };
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For 首段（反向代理场景），否则 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
