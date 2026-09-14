package com.example.classguard.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警表实体，对应表 alarms（见《数据库设计说明书（第8组）》V0.2 §4.2）。
 * <p>alarmType 取值：smoke / intrusion / break_in；level 取值：critical / warning（预留）；
 * status 取值：0 未处理 / 1 已处理。
 */
@Data
public class Alarm {

    /** 自增主键（API-06 使用） */
    private Long id;

    /** 告警唯一标识（设备生成，alarm-YYYYMMDD-四位序号），uk_alarm_id 去重依据 */
    private String alarmId;

    /** 教室编号，本期 c101，多教室预留 */
    private String classroomId;

    /** 设备编号，本期 dev-01 */
    private String deviceId;

    /** 告警类型：smoke / intrusion / break_in */
    private String alarmType;

    /** 告警级别：critical / warning（预留） */
    private String level;

    /** 告警人类可读描述，直接展示 */
    private String detail;

    /** 设备本地已自动执行的动作列表，JSON 数组字符串，如 ["buzzer_on","alarm_light_on","ventilation_on"] */
    private String actionTaken;

    /** 处理状态：0 未处理 / 1 已处理 */
    private Integer status;

    /** 处理人用户名，逻辑关联 users.username */
    private String handledBy;

    /** 处理时间（服务端时间） */
    private LocalDateTime handledAt;

    /** 告警时间 = 服务端接收时间（设备端无 RTC） */
    private LocalDateTime createdAt;
}
