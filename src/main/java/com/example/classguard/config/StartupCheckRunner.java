package com.example.classguard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动自检（REST 文档 §6.1 联调验收第 1 项：MySQL 连接成功、EMQX 订阅成功）。
 * <p>TODO: 待数据源与 MQTT 配置（D1 待定项）就绪后，在此依次自检并输出日志，
 * 任一失败则终止启动，避免演示时后端离线导致告警丢失。
 */
@Slf4j
@Component
public class StartupCheckRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        // TODO: ① MySQL 连通性自检；② MqttService.connectAndSubscribe 并确认订阅成功
        log.info("ClassGuard 启动完成（数据源/MQTT 自检待 D1 配置就绪后接入）");
    }
}
