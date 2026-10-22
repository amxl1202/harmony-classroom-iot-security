package com.example.classguard.constant;

/**
 * 设备指令白名单（《MQTT接口文档 v1.2》§8 指令表）。
 *
 * <p>用途：API-09 在下发前按本枚举校验 command 与 paras，非法指令直接返回 1001，
 * 不再依赖设备回执 E01/E02 —— 既省掉 3 秒等待，也避免把非法报文透传到设备端。
 *
 * <p>注意：新增指令必须同步《MQTT接口文档》指令表与 APP 端 CommandName 常量（三端一致）。
 */
public enum CommandType {

    /** 门禁上锁（无参数） */
    DOOR_LOCK("door_lock", false),

    /** 门禁开锁（无参数，安全类指令，不受布防模式限制） */
    DOOR_UNLOCK("door_unlock", false),

    /** 消音：关闭蜂鸣器与告警灯（无参数，安全类指令） */
    ALARM_SILENCE("alarm_silence", false),

    /** 告警灯开（无参数） */
    ALARM_LIGHT_ON("alarm_light_on", false),

    /** 告警灯关（无参数） */
    ALARM_LIGHT_OFF("alarm_light_off", false),

    /** 通风开（无参数） */
    VENTILATION_ON("ventilation_on", false),

    /** 通风关（无参数） */
    VENTILATION_OFF("ventilation_off", false),

    /** 布防/撤防切换（需 paras.value = armed | disarmed） */
    SET_MODE("set_mode", true);

    /** 指令名（MQTT 报文 command 字段取值） */
    private final String code;

    /** 是否需要 paras.value 参数（仅 set_mode 需要） */
    private final boolean needsValue;

    CommandType(String code, boolean needsValue) {
        this.code = code;
        this.needsValue = needsValue;
    }

    public String getCode() {
        return code;
    }

    public boolean isNeedsValue() {
        return needsValue;
    }

    /**
     * 按指令名查找白名单枚举。
     *
     * @param code 请求体中的 command 字段
     * @return 匹配的枚举；不在白名单内返回 null
     */
    public static CommandType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CommandType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
