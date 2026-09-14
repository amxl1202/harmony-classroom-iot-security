package com.example.classguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * API-02 登录请求体（REST 文档 API-02，字段规则同 API-01）。
 */
@Data
public class LoginRequest {

    /** 用户名（4~20 位字母/数字/下划线） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（6~20 位） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码须为 6~20 位")
    private String password;
}
