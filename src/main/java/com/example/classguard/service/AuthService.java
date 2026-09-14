package com.example.classguard.service;

import com.example.classguard.dto.LoginRequest;
import com.example.classguard.dto.LoginResponse;
import com.example.classguard.dto.RegisterRequest;

/**
 * 认证业务（API-01 注册 / API-02 登录，均为开放接口）。
 */
public interface AuthService {

    /** API-01 注册：用户名唯一校验（重复 1004）→ BCrypt 加密存储 → role=user */
    void register(RegisterRequest request);

    /** API-02 登录：账号不存在 1002 / 密码错误 1003；成功签发 JWT 并返回 {token, username, role, expiresIn} */
    LoginResponse login(LoginRequest request);
}
