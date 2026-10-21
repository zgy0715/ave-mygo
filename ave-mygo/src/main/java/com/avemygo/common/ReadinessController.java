package com.avemygo.common;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 就绪探针：/health 只证明进程活着，/ready 还要证明依赖可用（数据库能连通）。
 * 负载均衡/容器编排用 /ready 决定是否把流量打进来，避免"进程活着但 DB 挂了"仍收流量。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReadinessController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean ok = one != null && one == 1;
            body.put("status", ok ? "UP" : "DEGRADED");
            body.put("db", ok ? "UP" : "DOWN");
            return ResponseEntity.status(ok ? 200 : 503).body(ApiResponse.ok(body));
        } catch (Exception e) {
            log.error("就绪检查失败：数据库不可用", e);
            body.put("status", "DOWN");
            body.put("db", "DOWN");
            return ResponseEntity.status(503).<ApiResponse<Map<String, Object>>>body(ApiResponse.ok(body));
        }
    }
}
