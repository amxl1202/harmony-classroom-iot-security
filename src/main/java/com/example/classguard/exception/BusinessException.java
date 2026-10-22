package com.example.classguard.exception;

import lombok.Getter;

/**
 * 业务异常：service/拦截器抛出，由 GlobalExceptionHandler 统一转为
 * {"code":xxx,"message":...,"data":...} 响应（REST 接口文档 §3 错误码体系）。
 *
 * <p>data 用途：部分错误码需要在错误响应中携带附加数据，最典型的是 3002 设备指令超时
 * （《REST 接口文档 v1.1》§3 API-09 超时响应示例）——data 携带
 * {"cmdId":...,"result":null,"reason":null,"state":null}，供前端凭 cmdId 关联晚到的回执。
 * 其余场景 data 恒为 null。
 *
 * <p>用法示例：
 * <pre>
 *   throw new BusinessException(ErrorCode.USER_EXISTS);                    // 使用默认文案
 *   throw new BusinessException(ErrorCode.DEVICE_CMD_TIMEOUT, "自定义提示"); // 覆盖文案
 *   throw new BusinessException(ErrorCode.DEVICE_CMD_TIMEOUT,
 *           new CommandResponse(cmdId, null, null, null));                  // 3002 携带 data={cmdId,...}
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /** 附加数据（如 3002 超时时携带 {cmdId,...}；大多数异常为 null）；transient 避免序列化堆栈场景牵连 */
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.data = null;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    /**
     * 带附加数据的业务异常（使用 ErrorCode 默认文案）。
     * 典型场景：3002 超时响应 data 携带 {cmdId,...}（REST v1.1 §3 API-09 超时响应示例）。
     *
     * @param errorCode 错误码枚举（文案取默认值）
     * @param data      附加数据，将随错误响应一并返回（可为 null）
     */
    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getDefaultMessage());
        this.code = errorCode.getCode();
        this.data = data;
    }

    /**
     * 带附加数据且自定义文案的业务异常。
     *
     * @param errorCode 错误码枚举（仅取 code）
     * @param message   覆盖默认文案的提示信息
     * @param data      附加数据，将随错误响应一并返回（可为 null）
     */
    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.code = errorCode.getCode();
        this.data = data;
    }
}
