package com.example.classguard.config;

import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器（设计说明书 §4.3）：统一校验 JWT 并注入当前用户信息。
 *
 * <p>规则：除 API-01/API-02（/api/auth/**，已在 WebConfig 排除）外，
 * 均需请求头 Authorization: Bearer &lt;token&gt;（REST 文档 §2）；
 * API-07/API-08/API-09（/api/records、/api/devices）与 API-10/API-11/API-12（/api/invites，邀请码管理）
 * 额外校验 role=admin（REST v1.2 文档标注"管理员"）。
 *
 * <p>preHandle 抛出的 BusinessException 由 GlobalExceptionHandler（@RestControllerAdvice）
 * 统一转为 {code,message} JSON 响应，错误码遵循 REST 文档 §3（2001 未登录 / 2003 无权限）。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** request attribute key：拦截器解析 token 后写入，Controller 读取使用 */
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_ROLE = "role";

    /** "Authorization: Bearer <token>" 中的前缀（REST 文档 §2），其后为真正的 token */
    private static final String BEARER_PREFIX = "Bearer ";

    /** JWT 工具：签发与解析共用同一 secret */
    @Resource
    private JwtUtil jwtUtil;

    /**
     * 请求前置校验：验签 → 提取用户信息 → 管理员接口权限判断。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>OPTIONS 预检请求直接放行；</li>
     *   <li>校验 Authorization 头存在且以 "Bearer " 开头；</li>
     *   <li>JwtUtil.parse 验签并校验过期，失败抛 2001；</li>
     *   <li>解析出的 userId/username/role 写入 request attribute 供 Controller 获取；</li>
     *   <li>/api/records、/api/devices、/api/invites 要求 role=admin，否则抛 2003。</li>
     * </ol>
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ① OPTIONS 请求为 CORS 预检（浏览器自动发起、不携带自定义头），必须直接放行，否则跨域全部失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ② 取 Authorization 头：缺失或不以 "Bearer " 开头 → 未登录（REST 文档 §2）
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // ③ 截取 token（去掉 "Bearer " 前缀并去除首尾空白），验签 + 校验过期
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException e) {
            // ExpiredJwtException（过期）/ SignatureException（签名不符）/ MalformedJwtException（格式非法）
            // 等均为 JwtException 子类，统一按"未登录或 token 失效"处理（错误码 2001）
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // ④ 解析成功：写入 request attribute，Controller 通过 request.getAttribute(ATTR_*) 获取当前用户
        //    JWT JSON 载荷中的整数默认反序列化为 Integer，先取 Number 再 longValue() 避免 ClassCastException
        Number userIdNum = claims.get("userId", Number.class);
        long userId = userIdNum == null ? 0L : userIdNum.longValue();
        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_USERNAME, username);
        request.setAttribute(ATTR_ROLE, role);

        // ⑤ 管理员专属接口（API-07 打卡记录 / API-08 设备管理 / API-09 远程控制，
        //    以及 API-10/11/12 邀请码管理 /api/invites，REST v1.2 文档标注"管理员"）：
        //    role 非 admin → 无权限（错误码 2003）
        String uri = request.getRequestURI();
        boolean adminOnly = uri.startsWith("/api/records") || uri.startsWith("/api/devices")
                || uri.startsWith("/api/invites");
        if (adminOnly && !"admin".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // ⑥ 校验全部通过，放行至 Controller
        return true;
    }
}
