package com.example.classguard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * API-09 设备指令下发请求体（REST 文档 API-09，指令枚举见 MQTT 文档 §8）。
 * <p>command 取值：door_lock / door_unlock / alarm_silence / alarm_light_on / alarm_light_off /
 * ventilation_on / ventilation_off / set_mode。
 */
@Data
public class CommandRequest {

    /** 指令名（见上述枚举，非法值由设备回 E01） */
    @NotBlank(message = "command 不能为空")
    private String command;

    /** set_mode 时为 {"value":"armed"或"disarmed"}，其余为 {} */
    private Map<String, Object> paras = new HashMap<>();
}
