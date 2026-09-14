package com.example.classguard.service.impl;

import com.example.classguard.dto.CommandResponse;
import com.example.classguard.entity.Device;
import com.example.classguard.mapper.DeviceMapper;
import com.example.classguard.service.DeviceService;
import com.example.classguard.service.MqttService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备业务实现（API-08 / API-09；MQTT status 建档）。
 */
@Slf4j
@Service
public class DeviceServiceImpl implements DeviceService {

    /** 教室编号（本期固定 c101，多教室预留，见 REST 文档 API-09） */
    private static final String CLASSROOM_ID = "c101";

    /** cmd_resp 回执等待上限 3 秒（REST 文档 §5 / MQTT 文档 §9） */
    private static final long CMD_RESP_TIMEOUT_MS = 3000L;

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private MqttService mqttService;

    /** TODO: 待 MqttService 实现后，以其接收的 cmd_resp 填充（key=cmd_id），供 sendCommand 同步等待匹配 */
    private final Map<String, CommandResponse> cmdRespCache = new ConcurrentHashMap<>();

    @Override
    public List<Device> listDevices() {
        // TODO: deviceMapper.selectAll（API-08，管理员）
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public CommandResponse sendCommand(String deviceId, String command, Map<String, Object> paras, String issuedBy) {
        // TODO: ① 生成 cmd_id（cmd-YYYYMMDD-四位序号，后端生成）；
        //       ② 组装 cmd 信封（v=1, classroom_id=CLASSROOM_ID, device_id, msg_type=cmd, cmd_id, issued_by, command, paras）；
        //       ③ mqttService.publish 到 classroom/{cid}/device/{did}/cmd（QoS1）；
        //       ④ 同步等待 cmd_resp ≤3s：命中返回 {cmdId, result, reason, state}；
        //          超时或设备离线（cleanSession=true 无离线排队）返回 code=3002
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public void updateOnlineStatus(String classroomId, String deviceId, boolean online) {
        // TODO: deviceMapper.upsert（status 消息与 LWT 遗嘱共用；首次上线自动建档）
        throw new UnsupportedOperationException("待实现");
    }
}
