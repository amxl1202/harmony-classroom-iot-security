package com.example.classguard.service;

/**
 * MQTT 订阅模块（设计说明书 §4.4）：以 backend 账号、ClientID backend-service 连接 EMQX，
 * 订阅 classroom/+/device/+/# 全部上行主题并发布 cmd 主题。
 */
public interface MqttService {

    /** 连接 EMQX（keepAlive 60s、cleanSession true）并订阅 classroom/+/device/+/#；断线自动重连，重连成功后恢复订阅 */
    void connectAndSubscribe();

    /** 以 backend 账号发布消息（cmd 主题 QoS1） */
    void publish(String topic, String payload, int qos);

    /** 断开连接 */
    void disconnect();
}
