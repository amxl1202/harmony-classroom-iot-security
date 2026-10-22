package com.example.classguard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * cmd 指令下发上下文（《数据库设计说明书 V0.3》§4.3）。
 *
 * <p>依据 DB V0.3 §4.3：cmd_resp 回执报文不携带 command 与 issued_by 字段，
 * 因此后端发布 cmd 时（MQTT v1.2 §8 信封含这两个字段）以 cmd_id 为键将本上下文
 * 缓存于 DeviceServiceImpl#cmdContexts；收到 cmd_resp 回执后，据此合并补全
 * command/issued_by 写入 device_records 指令执行记录。
 *
 * <p>特别地：API-09 等待 3 秒超时（3002）后该上下文仍保留在缓存中，
 * 供晚到的 cmd_resp 回执兜底入库（由 DeviceService#getCmdContext 读取、
 * #removeCmdContext 在入库完成后移除）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CmdContext {

    /** 指令唯一标识（cmd-YYYYMMDD-四位序号，后端生成，MQTT v1.2 §4） */
    private String cmdId;

    /** 教室编号（本期固定 c101，多教室预留） */
    private String classroomId;

    /** 设备编号（cmd 报文的目标设备） */
    private String deviceId;

    /** 指令名称（如 open_door / close_door / alarm_on / alarm_off，cmd_resp 不回传，需缓存） */
    private String command;

    /** 下发人账号（取自 JWT，cmd_resp 不回传，需缓存） */
    private String issuedBy;

    /**
     * 上下文创建时刻（System.currentTimeMillis）。
     * <p>仅用于超时清理：无回执的指令（设备离线、报文丢失）不会触发 removeCmdContext，
     * 若不按时间淘汰，cmdContexts 会随运行时长无限增长（内存泄漏）。
     */
    private long createdAtMillis;
}
