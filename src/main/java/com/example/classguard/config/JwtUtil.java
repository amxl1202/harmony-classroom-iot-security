package com.example.classguard.config;

import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

/**
 * JWT 工具（设计说明书 §4.3）：HMAC256 签发，载荷 userId/username/role，有效期 7 天（604800 秒）。
 * <p>TODO: secret 与有效期从 application.properties 注入（jwt.secret / jwt.expire-seconds，D1 待定项）。
 */
@Component
public class JwtUtil {

    /** 签发 token（API-02 登录成功后调用），返回形如 eyJhbGciOi... 的 JWT */
    public String generate(Long userId, String username, String role) {
        // TODO: Jwts.builder() 设置 claim("userId")/subject(username)/claim("role") 与过期时间，HMAC256 签名
        throw new UnsupportedOperationException("待实现");
    }

    /** 解析并校验 token（拦截器鉴权使用）；签名不符或已过期抛出异常，由调用方转为 2001 */
    public Claims parse(String token) {
        // TODO: Jwts.parserBuilder() 用同一 secret 校验签名与过期，返回 Claims
        throw new UnsupportedOperationException("待实现");
    }
}
