package com.example.classguard.service;

/**
 * MQTT 订阅模块（设计说明书 §4.4 / MQTT 接口文档 v1.2 §2/§3/§8）：以 backend 账号、
 * ClientID backend-service 连接 EMQX，订阅 classroom/+/device/+/# 全部上行主题并发布 cmd 主题。
 *
 * <p>本模块不直接依赖任何业务服务：上行消息通过 {@link MqttMessageListener} 回调分发，
 * 监听器由启动流程（StartupCheckRunner）注册，避免
 * DeviceService → MqttService → 业务服务 → DeviceService 的循环依赖。
 */
public interface MqttService {

    /** 注册上行消息监听器（启动时注册 MqttMessageProcessor，可注册多个） */
    void registerListener(MqttMessageListener listener);

    /** 连接 EMQX（MQTT 接口文档 v1.2 §2：keepAlive 60s、cleanSession true）并订阅 classroom/+/device/+/#（v1.2 §3）；断线自动重连，重连成功后恢复订阅 */
    void connectAndSubscribe();

    /** 以 backend 账号发布消息（cmd 主题 QoS1，报文格式见 MQTT 接口文档 v1.2 §8） */
    void publish(String topic, String payload, int qos);

    /** 断开连接 */
    void disconnect();
}
