package com.example.classguard.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举（REST 接口文档 v1.2 §3，与《系统设计说明书》4.2 一致，新增须双向同步）。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    PARAM_ERROR(1001, "参数错误"),

    USER_NOT_FOUND(1002, "账号不存在"),

    PASSWORD_ERROR(1003, "密码错误"),

    USER_EXISTS(1004, "账号已存在"),

    /**
     * 1005 邀请码无效：注册侧对「不存在 / 已过期 / 已作废」三种情况统一返回，
     * 不向未认证方暴露邀请码状态细节（REST v1.2 §3 / API-01）。
     */
    INVITE_CODE_INVALID(1005, "邀请码无效"),

    UNAUTHORIZED(2001, "未登录或 token 失效"),

    FORBIDDEN(2003, "无权限（需要管理员）"),

    ALARM_NOT_FOUND(3001, "告警不存在"),

    /** 3002 的提示文案取 REST 文档 API-09 超时响应原文 */
    DEVICE_CMD_TIMEOUT(3002, "设备未响应，请稍后重试"),

    /**
     * 3003 邀请码不存在：管理接口 API-12 按 id 作废邀请码时，
     * 目标记录查不到则返回本错误码（面向已认证管理员）。
     */
    INVITE_NOT_FOUND(3003, "邀请码不存在"),

    INTERNAL_ERROR(5000, "服务器内部错误");

    /** 错误码 */
    private final int code;

    /** 默认提示文案（可通过 BusinessException 构造器覆盖） */
    private final String defaultMessage;
}
