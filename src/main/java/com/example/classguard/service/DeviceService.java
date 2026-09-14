package com.example.classguard.service;

import com.example.classguard.dto.CommandResponse;
import com.example.classguard.entity.Device;

import java.util.List;
import java.util.Map;

/**
 * 设备业务（API-08 设备列表 / API-09 指令下发，均管理员；同时承接 MQTT status 消息的设备建档）。
 */
public interface DeviceService {

    /** API-08 设备列表与在线状态：online/lastOnlineAt 由 MQTT status/LWT 驱动 */
    List<Device> listDevices();

    /** API-09 指令下发：生成 cmd_id → MQTT 发布 cmd（QoS1，issued_by 取自 JWT）→ 同步等待 cmd_resp ≤3s；
     *  超时/设备离线返回 3002；result=fail 为 E0x，ignored 为 I0x；state 为执行后执行器状态快照 */
    CommandResponse sendCommand(String deviceId, String command, Map<String, Object> paras, String issuedBy);

    /** MQTT status 消息/LWT 更新设备在线状态：UPSERT（INSERT ... ON DUPLICATE KEY UPDATE），首次上线自动建档 */
    void updateOnlineStatus(String classroomId, String deviceId, boolean online);
}
