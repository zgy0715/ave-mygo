package com.avemygo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求追踪号过滤器：给每个请求分配 requestId，写入 MDC 与响应头。
 * - 优先复用上游传入的 X-Request-Id（网关/前端透传时链路不中断）
 * - 日志格式在 application.yaml 的 logging.pattern 里引用 %X{requestId}
 * - finally 清理 MDC，防止线程池复用导致串号
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = (incoming != null && !incoming.isBlank() && incoming.length() <= 64)
                ? incoming.trim()
                : UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
