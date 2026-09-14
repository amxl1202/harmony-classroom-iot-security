package com.example.classguard.config;

/**
 * MQTT 客户端配置（设计说明书 §4.1 config：MQTT 客户端 Bean / §4.4 订阅模块设计）。
 *
 * <p>TODO(D1 待定项)：broker 地址:端口与 backend 账号密码确认后，
 * 在 application.properties 增加 mqtt.broker / mqtt.username / mqtt.password 配置，
 * 并在本类声明 Paho MqttClient Bean：
 * <ul>
 *   <li>ClientID = backend-service（全局唯一，防互踢）</li>
 *   <li>keepAlive = 60s，cleanSession = true</li>
 *   <li>无 TLS，仅限校园内网</li>
 * </ul>
 * Bean 就绪后由 StartupCheckRunner 触发 MqttService.connectAndSubscribe 完成订阅自检。
 */
// @Configuration // TODO: 待 D1 参数确定后启用并装配 MqttClient Bean
public class MqttConfig {
}
