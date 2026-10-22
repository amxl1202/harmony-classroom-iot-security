package com.example.classguard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（设计说明书 §4.3）：负责 token 的签发与解析校验，全局唯一入口。
 *
 * <p>契约出处：
 * <ul>
 *   <li>《系统设计说明书》§4.3 —— token 载荷包含 userId/username/role 三项业务字段，
 *       签名算法 HMAC256（HS256），有效期 7 天（604800 秒）；</li>
 *   <li>REST 接口文档 v1.1 API-02 —— 登录成功后签发 token，响应字段 expiresIn = 604800（秒），
 *       客户端后续请求统一携带 Authorization: Bearer &lt;token&gt;（REST 文档 §2）。</li>
 * </ul>
 *
 * <p>secret 与有效期由 application.properties 注入（jwt.secret / jwt.expire-seconds）。
 */
@Component
public class JwtUtil {

    /** 签名密钥字符串（jwt.secret，本地开发密钥，部署生产前可更换） */
    @Value("${jwt.secret}")
    private String secret;

    /** token 有效期（秒）：REST 文档 §2 规定 7 天 = 604800 秒；冒号后为未配置时的默认值 */
    @Value("${jwt.expire-seconds:604800}")
    private long expireSeconds;

    /**
     * 由 secret 字符串构造 HMAC-SHA256 签名密钥。
     *
     * <p>HS256 要求密钥长度 ≥ 32 字节（256 位），否则 Keys.hmacShaKeyFor 会抛出
     * WeakKeyException；因此 jwt.secret 配置为 64 位十六进制字符串（32 字节）。
     *
     * @return 用于 signWith / setSigningKey 的对称密钥
     */
    private SecretKey key() {
        // 将配置的密钥字符串按 UTF-8 编码为字节数组，包装成 HMAC-SHA256 对称密钥
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 JWT token（REST 文档 API-02 登录成功后由 AuthService 调用）。
     *
     * <p>载荷设计（设计说明书 §4.3）：sub = username，自定义 claim 携带 userId 与 role；
     * iat = 签发时刻，exp = iat + expireSeconds 秒；签名算法 HS256。
     *
     * @param userId   用户 ID（users 表主键，拦截器解析后写入 request attribute）
     * @param username 登录账号（置于标准声明 sub）
     * @param role     角色（student/teacher/admin，管理员接口鉴权依据）
     * @return 紧凑格式 JWT 字符串，形如 eyJhbGciOiJIUzI1NiJ9...
     */
    public String generate(Long userId, String username, String role) {
        // 签发时刻（iat），同时作为过期时间的计算基准
        Date now = new Date();
        // 过期时刻（exp）= 当前毫秒 + 有效期秒数 * 1000
        Date expiry = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                // sub：标准声明，存放登录账号 username
                .setSubject(username)
                // 自定义声明：用户 ID（Controller 与日志链路据此识别当前用户）
                .claim("userId", userId)
                // 自定义声明：角色，/api/records、/api/devices 管理员鉴权使用
                .claim("role", role)
                // iat：签发时间
                .setIssuedAt(now)
                // exp：过期时间
                .setExpiration(expiry)
                // HS256 + 本地密钥签名（与 parse 使用同一把 key）
                .signWith(key(), SignatureAlgorithm.HS256)
                // 三段式 Base64URL 序列化，输出最终 token
                .compact();
    }

    /**
     * 解析并校验 JWT（JwtInterceptor 鉴权调用）。
     *
     * <p>使用与签发时相同的 secret 验签：token 被篡改抛 SignatureException、
     * 已过期抛 ExpiredJwtException、格式非法抛 MalformedJwtException，
     * 三者均为 JwtException 系异常——由拦截器统一 catch 后转为
     * BusinessException(UNAUTHORIZED)（错误码 2001，见 GlobalExceptionHandler）。
     *
     * @param token 去除 "Bearer " 前缀后的 JWT 字符串
     * @return Claims 载荷（可取出 userId/username/role）
     */
    public Claims parse(String token) {
        // parserBuilder 指定验签密钥后构建解析器；parseClaimsJws 同步校验签名与 exp 过期
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 获取 token 有效期（秒）。
     *
     * <p>供登录接口（API-02）组装响应字段 expiresIn 使用（REST 文档：expiresIn = 604800）。
     *
     * @return 有效期秒数，默认 604800（7 天）
     */
    public long getExpireSeconds() {
        return expireSeconds;
    }
}
