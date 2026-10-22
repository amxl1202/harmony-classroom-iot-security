package com.example.classguard.service.impl;

import com.example.classguard.dto.CmdContext;
import com.example.classguard.dto.CommandResponse;
import com.example.classguard.entity.Alarm;
import com.example.classguard.entity.DeviceRecord;
import com.example.classguard.service.AlarmService;
import com.example.classguard.service.DeviceService;
import com.example.classguard.service.MqttMessageListener;
import com.example.classguard.service.RecordService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MQTT 上行消息处理器：解析 JSON 并按 msg_type 分发到业务服务
 * （设计说明书 §4.4 / MQTT 接口文档 v1.2 §4/§10 / 数据库设计说明书 V0.3 §6.2/§4.3）。
 *
 * <p>独立于 MqttService 存在：MqttService 只负责连接/订阅/发布，收到消息后回调本类，
 * 由本类依赖业务服务——从而切断 MqttService 与 DeviceService 之间的循环依赖。
 *
 * <p>分发规则（MQTT 接口文档 v1.2 §4/§5/§6/§7/§8）：
 * <ul>
 *   <li>data     → 仅内存与日志，不落库（DB V0.3 §6.2）；</li>
 *   <li>status   → 设备在线状态 UPSERT 建档（含 LWT online=false，v1.2 §6）；</li>
 *   <li>alarm    → 组装 Alarm 按 alarm_id 去重入库（uk_alarm_id）；</li>
 *   <li>cmd_resp → 唤醒 API-09 同步等待 + 按下发上下文合并入库（DB V0.3 §4.3）；</li>
 *   <li>无法解析的 JSON / 缺公共字段 / 未知 msg_type → 记日志直接丢弃，不回执（v1.2 §10）。</li>
 * </ul>
 */
@Slf4j
@Component
public class MqttMessageProcessor implements MqttMessageListener {

    /** Jackson 对象映射器（Spring Boot 自动配置 Bean，线程安全可复用） */
    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AlarmService alarmService;

    @Resource
    private RecordService recordService;

    @Resource
    private DeviceService deviceService;

    @Override
    public void onMessage(String topic, String payload) {
        // ① JSON 解析：任何解析异常都直接丢弃且不回执（MQTT 接口文档 v1.2 §10）
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("无法解析的 JSON 消息，直接丢弃（MQTT v1.2 §10）：topic={}, payload={}, error={}", topic, payload, e.getMessage());
            return;
        }

        // ② 顶层公共字段（MQTT 接口文档 v1.2 §4 公共字段约定）
        String msgType = root.path("msg_type").asText("");
        String classroomId = root.path("classroom_id").asText("");
        String deviceId = root.path("device_id").asText("");
        if (msgType.isEmpty() || classroomId.isEmpty() || deviceId.isEmpty()) {
            log.warn("消息缺少公共字段，丢弃：topic={}, payload={}", topic, payload);
            return;
        }

        // ③ 按 msg_type 分发（Java 17 箭头 switch，无穿透）
        switch (msgType) {

            // 传感器数据上报：仅内存与日志，不落库（MQTT v1.2 §5 / DB V0.3 §6.2：data 不落库）
            case "data" -> log.debug("data 上报（仅内存与日志，不落库）：classroomId={}, deviceId={}, smoke={}, pir={}, door={}, work_mode={}",
                    classroomId, deviceId,
                    root.path("smoke").asText(), root.path("pir").asText(),
                    root.path("door").asText(), root.path("work_mode").asText());

            case "status" -> {
                // LWT 遗嘱消息仅携带 online=false（MQTT 接口文档 v1.2 §6），缺省按离线处理
                boolean online = root.path("online").asBoolean(false);
                // UPSERT 建档：设备首次上线自动建档，后续仅刷新在线状态
                deviceService.updateOnlineStatus(classroomId, deviceId, online);
                log.debug("status 更新：classroomId={}, deviceId={}, online={}", classroomId, deviceId, online);
            }

            case "alarm" -> {
                String alarmId = root.path("alarm_id").asText("");
                if (alarmId.isEmpty()) {
                    log.warn("alarm 消息缺少 alarm_id，丢弃：topic={}, payload={}", topic, payload);
                    return;
                }
                Alarm alarm = new Alarm();
                alarm.setAlarmId(alarmId);
                alarm.setClassroomId(classroomId);
                alarm.setDeviceId(deviceId);
                // type/level 缺省取文档默认值（smoke/critical）
                alarm.setAlarmType(root.path("type").asText("smoke"));
                alarm.setLevel(root.path("level").asText("critical"));
                alarm.setDetail(root.path("detail").asText());
                // 动作列表 JSON 数组原样字符串入库（REST v1.1 API-05 直接展示）；缺失或非数组 → NULL
                JsonNode actionNode = root.path("action_taken");
                if (actionNode.isArray()) {
                    alarm.setActionTaken(actionNode.toString());
                } else {
                    alarm.setActionTaken(null);
                }
                // 入库（saveFromMqtt 内部按 alarm_id 去重，重复上报丢弃）
                alarmService.saveFromMqtt(alarm);
                log.info("alarm 消息处理完成：classroomId={}, deviceId={}, alarmId={}, type={}, level={}",
                        classroomId, deviceId, alarmId, alarm.getAlarmType(), alarm.getLevel());
            }

            case "cmd_resp" -> {
                String cmdId = root.path("cmd_id").asText("");
                if (cmdId.isEmpty()) {
                    log.warn("cmd_resp 消息缺少 cmd_id，丢弃：topic={}, payload={}", topic, payload);
                    return;
                }
                CommandResponse resp = new CommandResponse();
                resp.setCmdId(cmdId);
                resp.setResult(root.path("result").asText(""));
                // success 时 reason 为空 → 置 null（DB V0.3：reason 列可空），fail/ignored 携带 E0x/I0x
                String reason = root.path("reason").asText("");
                if (reason.isEmpty()) {
                    resp.setReason(null);
                } else {
                    resp.setReason(reason);
                }
                // state 为执行后执行器状态快照（对象）→ 转 Map<String,String>
                JsonNode stateNode = root.path("state");
                if (stateNode.isObject()) {
                    resp.setState(objectMapper.convertValue(stateNode, new TypeReference<Map<String, String>>() { }));
                }

                // a) 唤醒 API-09 的 3 秒同步等待（无等待者时 DeviceService 内部仅记日志）
                deviceService.completeCommand(cmdId, resp);

                // b) DB V0.3 §4.3：cmd_resp 不携带 command/issued_by，须按下发缓存合并
                CmdContext ctx = deviceService.getCmdContext(cmdId);
                if (ctx == null) {
                    // 后端重启窗口或非本服务下发的 cmd_id：无法补全执行历史，跳过入库
                    log.warn("cmd_resp 无下发上下文（后端重启窗口或非本服务下发的 cmd_id），跳过执行历史入库：cmdId={}", cmdId);
                    return;
                }

                // c) 组装执行历史：classroomId/deviceId/command/issuedBy 取自下发上下文，result/reason 取自回执
                DeviceRecord record = new DeviceRecord();
                record.setCmdId(cmdId);
                record.setClassroomId(ctx.getClassroomId());
                record.setDeviceId(ctx.getDeviceId());
                record.setCommand(ctx.getCommand());
                record.setIssuedBy(ctx.getIssuedBy());
                record.setResult(resp.getResult());
                record.setReason(resp.getReason());
                // 入库（按 uk_cmd_id + INSERT IGNORE 幂等，同一 cmd_id 仅首次生效）
                recordService.saveFromMqtt(record);

                // d) 入库完成后清理下发上下文；重复回执第二次到达时 ctx 已清理，
                //    走上方"无下发上下文"分支跳过，天然幂等
                deviceService.removeCmdContext(cmdId);
                log.info("cmd_resp 回执处理完成：cmdId={}, result={}, command={}, issuedBy={}",
                        cmdId, resp.getResult(), ctx.getCommand(), ctx.getIssuedBy());
            }

            default -> log.warn("未知 msg_type，丢弃：{}", msgType);
        }
    }
}
