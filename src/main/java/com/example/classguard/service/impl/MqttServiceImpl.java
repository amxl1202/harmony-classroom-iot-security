package com.example.classguard.service.impl;

import com.example.classguard.service.AlarmService;
import com.example.classguard.service.DeviceService;
import com.example.classguard.service.MqttService;
import com.example.classguard.service.RecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MQTT 订阅模块实现（设计说明书 §4.4 / MQTT 接口文档 v1.1）。
 * <p>以 backend 账号、ClientID backend-service 连接 EMQX，订阅 classroom/+/device/+/#，
 * 按 msg_type 分类处理；断线自动重连并恢复订阅。
 */
@Slf4j
@Service
public class MqttServiceImpl implements MqttService {

    /** 后端订阅通配主题（全部上行：data/status/alarm/cmd_resp） */
    private static final String SUBSCRIBE_TOPIC = "classroom/+/device/+/#";

    @Resource
    private AlarmService alarmService;

    @Resource
    private RecordService recordService;

    @Resource
    private DeviceService deviceService;

    @Override
    public void connectAndSubscribe() {
        // TODO: ① Paho MqttClient 连接 EMQX（账号 backend，ClientID=backend-service，
        //          keepAlive=60s，cleanSession=true，地址/密码见 D1 待定项）；
        //       ② 订阅 SUBSCRIBE_TOPIC（QoS1）；
        //       ③ 消息回调按 msg_type 分发：
        //          alarm    → alarmService.saveFromMqtt（按 alarm_id 去重入库）
        //          cmd_resp → recordService.saveFromMqtt（按 cmd_id 幂等入库，并唤醒 API-09 等待）
        //          status   → deviceService.updateOnlineStatus（含 LWT online=false，UPSERT）
        //          data     → 仅更新内存与日志，不落库
        //       ④ 连接断开自动重连，重连成功后恢复订阅；
        //       ⑤ 已知边界（接受）：cleanSession=true，后端离线期间 QoS1 消息不排队，告警丢失不补传
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public void publish(String topic, String payload, int qos) {
        // TODO: 以 backend 账号发布消息（cmd 主题 QoS1），发布失败进入重连流程
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public void disconnect() {
        throw new UnsupportedOperationException("待实现");
    }
}
