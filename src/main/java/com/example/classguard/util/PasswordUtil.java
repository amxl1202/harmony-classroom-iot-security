package com.example.classguard.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码工具（设计说明书 §4.1 util：BCrypt 密码工具）。
 * <p>口令经 BCrypt 加盐哈希后存入 users.password（VARCHAR(100)），禁止明文落库；
 * 预置管理员 admin（admin123）初始化使用同一机制。
 */
@Component
public class PasswordUtil {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 加密（注册 API-01 / 修改密码 API-03 / 预置管理员初始化时使用） */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /** 校验（登录 API-02 / 修改密码 API-03 时使用） */
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
