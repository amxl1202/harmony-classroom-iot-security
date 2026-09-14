package com.example.classguard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API-02 登录响应 data（REST 文档 API-02）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT，后续请求放入 Authorization: Bearer <token> */
    private String token;

    /** 用户名 */
    private String username;

    /** 角色：user / admin */
    private String role;

    /** token 有效期秒数（604800，即 7 天） */
    private long expiresIn;
}
