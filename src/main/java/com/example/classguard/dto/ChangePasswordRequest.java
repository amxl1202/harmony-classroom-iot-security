package com.example.classguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * API-03 修改密码请求体（REST 文档 API-03）。
 * <p>边界：本期不做 token 吊销，修改密码后旧 token 在有效期内仍有效。
 */
@Data
public class ChangePasswordRequest {

    /** 原密码，校验错误返回 1003 */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码，6~20 位（同注册规则），不符合规则返回 1001 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码须为 6~20 位")
    private String newPassword;
}
