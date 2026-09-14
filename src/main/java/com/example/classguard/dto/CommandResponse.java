package com.example.classguard.dto;

import lombok.Data;

import java.util.Map;

/**
 * API-09 设备指令下发响应 data（REST 文档 API-09）。
 * <p>result：success / fail（reason 为 E0x）/ ignored（reason 为 I0x）；
 * state 为执行后最新执行器状态快照（与 status.actuators 结构一致）。
 * <p>超时（3 秒内未收到 cmd_resp）返回 code=3002，data 中 cmdId/result/reason/state 为 null。
 */
@Data
public class CommandResponse {

    /** 指令唯一标识（cmd-YYYYMMDD-四位序号） */
    private String cmdId;

    /** 执行结果：success / fail / ignored */
    private String result;

    /** fail 时为 E0x，ignored 时为 I0x，success 时为 null */
    private String reason;

    /** 执行后最新执行器状态：door_lock/buzzer/alarm_light/ventilation */
    private Map<String, String> state;
}
