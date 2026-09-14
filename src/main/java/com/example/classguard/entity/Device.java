package com.example.classguard.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备档案与在线状态表实体，对应表 devices（见《数据库设计说明书（第8组）》V0.2 §4.4）。
 * <p>联合自然主键 (classroom_id, device_id)；online 由 MQTT status 消息与 LWT 遗嘱驱动，
 * 写入采用 UPSERT（INSERT ... ON DUPLICATE KEY UPDATE，设备首次上线自动建档）。
 */
@Data
public class Device {

    /** 教室编号，本期 c101，多教室预留（联合主键之一） */
    private String classroomId;

    /** 设备编号，本期 dev-01（联合主键之一） */
    private String deviceId;

    /** 设备名称，如"1号教室安防主机" */
    private String deviceName;

    /** 在线状态：0 离线 / 1 在线 */
    private Integer online;

    /** 最近一次 status/LWT 状态更新时间（服务端时间） */
    private LocalDateTime lastOnlineAt;
}
