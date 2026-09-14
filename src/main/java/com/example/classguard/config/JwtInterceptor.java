package com.example.classguard.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器（设计说明书 §4.3）。
 * <p>规则：除 API-01/API-02（/api/auth/**，已在 WebConfig 排除）外，
 * 均需请求头 Authorization: Bearer &lt;token&gt;；API-07/API-08/API-09 额外校验 role=admin。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** request attribute key：拦截器解析 token 后写入，Controller 读取使用 */
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_ROLE = "role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // TODO: ① 取 Authorization 头，去掉 "Bearer " 前缀，JwtUtil.parse 校验；
        //          缺失/解析失败/已过期 → 统一响应 code=2001（未登录或 token 失效）并返回 false；
        //       ② 将 userId/username/role 写入 request attribute；
        //       ③ /api/records、/api/devices 路径校验 role=admin，否则 code=2003（无权限）并返回 false
        return true; // 占位：待 JwtUtil 实现后接入校验逻辑
    }
}
