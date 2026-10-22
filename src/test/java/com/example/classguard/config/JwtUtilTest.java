package com.example.classguard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 单元测试（设计说明书 §4.3 / REST 文档 §2）。
 *
 * <p>覆盖三件事：正常签发与解析时业务载荷不丢、过期 token 被拒、签名被篡改的 token 被拒。
 * 这三个点是 JWT 鉴权的最小可信集，也是 JwtInterceptor 里 catch JwtException 的契约来源。
 *
 * <p>不启动 Spring 上下文：直接 new JwtUtil 并用 ReflectionTestUtils 注入 @Value 字段，
 * 保证测试可在无 MySQL / 无 EMQX 的环境运行。
 */
class JwtUtilTest {

    /** 测试密钥：64 位十六进制串（32 字节，满足 HS256 最小密钥长度） */
    private static final String SECRET = "43251c56597c555b5533232e364bc17cc20f3bdb03caa6de484edeb676ff1116";

    /** 7 天 = 604800 秒（REST 文档 API-02 的 expiresIn） */
    private static final long EXPIRE_SECONDS = 604800L;

    /**
     * 构造被测对象并注入配置项（等价于 application.properties 的 jwt.secret / jwt.expire-seconds）。
     *
     * @param secret        签名密钥
     * @param expireSeconds 有效期秒数（负数表示已过期）
     * @return 可直接使用的 JwtUtil
     */
    private JwtUtil newJwtUtil(String secret, long expireSeconds) {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expireSeconds", expireSeconds);
        return jwtUtil;
    }

    /**
     * 正常路径：签发的 token 能被解析，且 userId / username / role 三项业务载荷完整。
     */
    @Test
    @DisplayName("签发后可解析，userId/username/role 不丢失")
    void generateThenParseShouldKeepClaims() {
        JwtUtil jwtUtil = newJwtUtil(SECRET, EXPIRE_SECONDS);

        String token = jwtUtil.generate(7L, "admin", "admin");
        assertNotNull(token, "签发的 token 不应为 null");

        Claims claims = jwtUtil.parse(token);
        // sub 存 username；JSON 数字默认反序列化为 Integer，故用 Number 取值（与 JwtInterceptor 一致）
        assertEquals("admin", claims.getSubject());
        assertEquals("admin", claims.get("role", String.class));
        assertEquals(7L, claims.get("userId", Number.class).longValue());
        assertNotNull(claims.getExpiration(), "exp 必须存在");
    }

    /**
     * 过期路径：有效期设为负数后签发，解析必须抛 ExpiredJwtException。
     * 该异常是 JwtException 子类，JwtInterceptor 统一转换为 2001。
     */
    @Test
    @DisplayName("过期 token 解析抛 ExpiredJwtException")
    void parseExpiredTokenShouldThrow() {
        // 有效期 -3600 秒（1 小时前过期），避免秒级取整导致恰好未过期
        JwtUtil jwtUtil = newJwtUtil(SECRET, -3600L);
        String expired = jwtUtil.generate(1L, "user01", "user");

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parse(expired));
    }

    /**
     * 篡改路径：改动签名段的第一个字符后解析必须抛 JwtException（签名校验失败）。
     * 改首字符而不是末字符，是为了避开 Base64URL 末尾填充位可能不改变解码结果的情况。
     */
    @Test
    @DisplayName("签名被篡改的 token 解析抛 JwtException")
    void parseTamperedTokenShouldThrow() {
        JwtUtil jwtUtil = newJwtUtil(SECRET, EXPIRE_SECONDS);
        String token = jwtUtil.generate(1L, "user01", "user");

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT 应为三段式");
        char first = parts[2].charAt(0);
        char replaced = first == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + replaced + parts[2].substring(1);

        assertThrows(JwtException.class, () -> jwtUtil.parse(tampered));
    }
}
