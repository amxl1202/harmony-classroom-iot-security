package com.example.classguard.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备执行记录表实体，对应表 device_records（见《数据库设计说明书（第8组）》V0.2 §4.3）。
 * <p>由 MQTT cmd_resp 回执按 cmd_id 幂等写入（唯一索引 uk_cmd_id + INSERT IGNORE）。
 */
@Data
public class DeviceRecord {

    /** 自增主键 */
    private Long id;

    /** 指令唯一标识（后端生成，cmd-YYYYMMDD-四位序号），回执幂等依据 */
    private String cmdId;

    /** 教室编号，本期 c101，多教室预留 */
    private String classroomId;

    /** 设备编号，本期 dev-01 */
    private String deviceId;

    /** 指令名：door_lock/door_unlock/alarm_silence/alarm_light_on/alarm_light_off/ventilation_on/ventilation_off/set_mode */
    private String command;

    /** 指令发起人用户名（取自 JWT），逻辑关联 users.username */
    private String issuedBy;

    /** 执行结果：success / fail / ignored */
    private String result;

    /** 错误码：fail 填 E0x，ignored 填 I0x，success 为空，可附加英文补充说明 */
    private String reason;

    /** 记录时间 = 服务端接收回执时间 */
    private LocalDateTime createdAt;
}
