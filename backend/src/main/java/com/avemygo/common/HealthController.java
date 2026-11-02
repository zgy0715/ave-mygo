package com.avemygo.common;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M1 端到端验证接口：应用 + 数据库都活着，它才能返回 200。
 * 前端首页连通这个接口即打通"前端 -> 后端 -> MySQL"最小链路。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "app", "ave-mygo",
                "time", LocalDateTime.now().toString()
        ));
    }
}
