package com.example.classguard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 客户端连接参数配置（MQTT 接口文档 v1.2 §2 / 设计说明书 §4.4 订阅模块设计）。
 *
 * <p>通过 @ConfigurationProperties(prefix = "mqtt") 从 application.properties 松弛绑定：
 * <pre>
 *   mqtt.broker     -> broker     （如 tcp://localhost:11883）
 *   mqtt.username   -> username   （backend 专用账号）
 *   mqtt.password   -> password
 *   mqtt.client-id  -> clientId   （松弛绑定：短横线自动映射驼峰）
 * </pre>
 *
 * <p>连接约定（MQTT 接口文档 v1.2 §2）：
 * <ul>
 *   <li>ClientID 全局唯一（backend-service），防止多实例互踢下线；</li>
 *   <li>KeepAlive 60s、Clean Session true；</li>
 *   <li>无 TLS（明文 tcp://），仅限校园内网使用；</li>
 *   <li>D1 已确认 EMQX 5.3.2，MQTT 端口 11883（非默认 1883）。</li>
 * </ul>
 *
 * <p>Bean 就绪后由 StartupCheckRunner 触发 MqttService.connectAndSubscribe 完成订阅自检。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {

    /** EMQX 服务器地址（tcp://host:port），D1 确认：本机 EMQX 5.3.2 端口 11883 */
    private String broker;

    /** MQTT 登录账号（backend 专用，EMQX 内置数据库认证） */
    private String username;

    /** MQTT 登录密码 */
    private String password;

    /** 客户端 ID：全局唯一（backend-service），防止重复连接互踢 */
    private String clientId;
}
