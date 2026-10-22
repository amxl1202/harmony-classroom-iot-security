package com.example.classguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * API-01 注册请求体（REST 文档 API-01）。
 * <p>自《改进方案-邀请码注册》v1.1 起，注册受邀请码约束：
 * 须携带管理员经 API-10 生成的有效邀请码，否则注册失败。
 */
@Data
public class RegisterRequest {

    /** 4~20 位字母/数字/下划线，全局唯一，重复返回 1004 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^\\w{4,20}$", message = "用户名须为 4~20 位字母/数字/下划线")
    private String username;

    /** 6~20 位，无复杂度要求 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码须为 6~20 位")
    private String password;

    /** 邀请码，8 位，管理员经 API-10 生成，必填；不存在/已过期/已作废统一返回 1005（REST v1.2 API-01） */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
