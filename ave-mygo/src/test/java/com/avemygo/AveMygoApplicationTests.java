package com.avemygo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 上下文加载测试：依赖 Testcontainers（需要 Docker）。
 * M1 阶段本机未装 Docker，暂时禁用；M5 质量加固时恢复并接入真实测试。
 */
@Disabled("M5 启用：需要 Docker 运行 Testcontainers")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AveMygoApplicationTests {

    @Test
    void contextLoads() {
    }

}
