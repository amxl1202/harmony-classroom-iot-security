package com.example.classguard.config;

import com.example.classguard.entity.User;
import com.example.classguard.mapper.UserMapper;
import com.example.classguard.service.MqttService;
import com.example.classguard.service.impl.MqttMessageProcessor;
import com.example.classguard.util.PasswordUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 应用启动自检与 MQTT 监听器装配（REST 接口文档 v1.1 §6.1 联调验收第 1 项：MySQL 连接成功、EMQX 订阅成功）。
 * <p>启动顺序：注册上行消息监听器 → MySQL 连通性自检 → 预置管理员初始化（幂等）→
 * EMQX 连接与订阅自检；任一环节失败抛出 IllegalStateException 终止启动，
 * 避免演示时后端离线导致告警丢失。
 * <p>监听器注册放在启动流程而非 MqttService 内部注入，避免循环依赖。
 */
@Slf4j
@Component
public class StartupCheckRunner implements CommandLineRunner {

    /**
     * 是否在启动时强校验外部依赖（MySQL / EMQX）。
     *
     * <p>生产与演示环境保持 true（失败即终止启动，避免演示时后端带病运行导致告警丢失）；
     * 单元测试环境置 false（application-test.properties），否则 @SpringBootTest 也必须
     * 依赖真实的 MySQL 与 EMQX 才能启动，等于无法写测试。
     */
    @Value("${app.startup-check.enabled:true}")
    private boolean startupCheckEnabled;

    @Resource
    private MqttService mqttService;

    @Resource
    private MqttMessageProcessor mqttMessageProcessor;

    /** MySQL 数据源（自检 1：连通性） */
    @Resource
    private DataSource dataSource;

    /** 用户表访问（自检 2：预置管理员幂等初始化） */
    @Resource
    private UserMapper userMapper;

    /** BCrypt 密码工具（预置管理员口令加盐哈希） */
    @Resource
    private PasswordUtil passwordUtil;

    @Override
    public void run(String... args) {
        // 注册上行消息监听器（不依赖 broker，启动即执行）
        mqttService.registerListener(mqttMessageProcessor);

        // 测试/离线调试场景可关闭外部依赖自检：监听器仍需注册，避免测试时 NPE
        if (!startupCheckEnabled) {
            log.info("启动自检已关闭（app.startup-check.enabled=false），跳过 MySQL 与 EMQX 校验");
            return;
        }

        // ① MySQL 连通性自检（REST v1.1 §6.1 联调验收第 1 项）
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            log.info("启动自检 1/3：MySQL 连接成功");
        } catch (Exception e) {
            // 自检失败终止启动，避免演示时后端离线导致告警丢失
            log.error("启动自检失败：MySQL 连接失败，终止启动", e);
            throw new IllegalStateException("MySQL 连接失败", e);
        }

        // ② 预置管理员初始化（设计说明书 §4.3 / DB V0.3 §7.2：admin/admin123）
        //    幂等：不存在才创建；已存在跳过且不重置密码（保留管理员已修改的口令）
        User existing = userMapper.selectByUsername("admin");
        if (existing == null) {
            User admin = new User();
            admin.setUsername("admin");
            // BCrypt 加盐哈希后落库，禁止明文
            admin.setPassword(passwordUtil.encode("admin123"));
            admin.setRole("admin");
            userMapper.insert(admin);
            log.info("启动自检 2/3：预置管理员 admin 初始化完成（初始密码 admin123，请在首次登录后修改）");
        } else {
            log.info("启动自检 2/3：预置管理员已存在，跳过初始化（不重置密码）");
        }

        // ③ EMQX 连接与订阅自检；connectAndSubscribe 内部失败会抛 IllegalStateException 终止启动
        mqttService.connectAndSubscribe();
        log.info("启动自检 3/3：EMQX 连接与订阅成功（classroom/+/device/+/#）");

        log.info("ClassGuard 启动自检全部通过，服务就绪：http://localhost:8080");
    }
}
