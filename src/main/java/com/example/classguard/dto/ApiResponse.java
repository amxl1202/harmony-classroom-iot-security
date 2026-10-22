package com.example.classguard.dto;

import lombok.Data;

/**
 * 统一响应壳：{"code":0,"message":"success","data":...}（REST 文档 §2）。
 * <p>code=0 表示成功；非 0 见 REST 文档 §3 错误码表
 * （1001 参数错误 / 1002 账号不存在 / 1003 密码错误 / 1004 账号已存在 /
 * 2001 未登录或 token 失效 / 2003 无权限 / 3001 告警不存在 / 3002 设备指令执行超时 / 5000 服务器内部错误）。
 */
@Data
public class ApiResponse<T> {

    private int code;

    private String message;

    private T data;

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "success";
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        return resp;
    }

    /**
     * 错误响应（携带 data）：供 3002 设备指令超时等需要在错误体中附带数据的场景，
     * 对应《REST 接口文档 v1.1》§3 错误码表中 API-09 的超时响应示例
     * {"code":3002,"message":"设备未响应，请稍后重试","data":{"cmdId":...,"result":null,...}}。
     *
     * @param code    错误码（如 3002）
     * @param message 错误提示文案
     * @param data    附加数据（3002 时为携带 cmdId 的 CommandResponse，可为 null）
     * @param <T>     data 的类型
     * @return 统一错误响应壳（code/message/data）
     */
    public static ApiResponse<Object> error(int code, String message, Object data) {
        ApiResponse<Object> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        resp.data = data;
        return resp;
    }
}
