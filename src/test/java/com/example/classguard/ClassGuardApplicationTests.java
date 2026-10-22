package com.example.classguard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用上下文加载测试。
 *
 * <p>使用 test profile 关闭启动自检（app.startup-check.enabled=false），
 * 使本测试不依赖真实的 MySQL 与 EMQX —— 否则 contextLoads 必须连着数据库和 broker 才能跑，
 * 等于把"有没有测试"与"环境有没有装齐"绑死（见 StartupCheckRunner）。
 */
@SpringBootTest
@ActiveProfiles("test")
class ClassGuardApplicationTests {

    @Test
    void contextLoads() {
    }

}
