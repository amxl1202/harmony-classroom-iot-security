package com.example.classguard.service;

/**
 * MQTT 上行消息监听器：MqttService 收到消息后回调
 * （消息格式与公共字段约定见《MQTT 接口文档 v1.2》§4）。
 * <p>实现类（{@link com.example.classguard.service.impl.MqttMessageProcessor}）负责解析 JSON
 * 并按 msg_type 分发到业务服务（alarm/cmd_resp/status/data）。
 */
@FunctionalInterface
public interface MqttMessageListener {

    /**
     * @param topic   消息主题，如 classroom/c101/device/dev-01/alarm
     * @param payload UTF-8 JSON 消息体
     */
    void onMessage(String topic, String payload);
}
