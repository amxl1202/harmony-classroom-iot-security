package com.example.classguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * API-01 注册请求体（REST 文档 API-01）。
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
}
