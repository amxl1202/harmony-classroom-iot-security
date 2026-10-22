package com.example.classguard.controller;

import com.example.classguard.dto.ApiResponse;
import com.example.classguard.dto.LoginRequest;
import com.example.classguard.dto.LoginResponse;
import com.example.classguard.dto.RegisterRequest;
import com.example.classguard.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（REST 接口文档 v1.2 API-01/API-02，开放接口，无需 token）。
 *
 * <p>本类下所有接口路径均为 {@code /api/auth/**}，已被 JwtInterceptor 排除拦截，
 * 即未携带 token 也可访问。v1.2 契约下注册（API-01）受邀请码约束：请求体必含 inviteCode（必填），
 * 服务端按 1001（参数）→ 1005（邀请码无效）→ 1004（用户名已存在）顺序校验；
 * 用户不存在（1002）、密码错误（1003）等业务错误由 AuthService 抛出 BusinessException，
 * 经全局异常处理器统一转换为 ApiResponse 错误壳，Controller 内不做 try/catch。</p>
 *
 * @author 第8组生产实习
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 认证业务逻辑（注册/登录），由 Spring 构造器注入。 */
    private final AuthService authService;

    /**
     * 构造器注入 AuthService。
     *
     * @param authService 认证服务实现
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * API-01 用户注册：POST /api/auth/register（开放接口）。
     *
     * <p>契约：《REST 接口文档 v1.2》API-01，注册受邀请码约束，请求体含 inviteCode（必填）。
     * 校验顺序：参数校验 1001（{@link RegisterRequest} 校验注解已内置，@Valid 触发）
     * → 邀请码懒校验 1005（服务层：不存在/已过期/已作废统一按"邀请码无效"处理）
     * → 用户名唯一 1004（服务层）。<br>
     * 业务错误码：1005 邀请码无效、1004 用户名已存在（均由服务层抛出）。</p>
     *
     * @param request 注册请求体（username/password/inviteCode，已通过 @Valid 校验）
     * @return 统一响应壳，code=0 表示注册成功，data 为空
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        // 邀请码无效（1005）/用户名重复（1004）由服务层抛 BusinessException，此处无需处理
        authService.register(request);
        // 注册成功，仅返回成功壳，不带数据
        return ApiResponse.ok();
    }

    /**
     * API-02 用户登录：POST /api/auth/login（开放接口）。
     *
     * <p>权限：无需登录。<br>
     * 请求体：{@link LoginRequest}（@Valid 校验）。<br>
     * 业务错误码：1002 用户不存在、1003 密码错误（均由服务层抛出）。<br>
     * 成功时 data 返回 {@link LoginResponse}：{@code {token, username, role, expiresIn}}
     * 其中 expiresIn 为 token 有效期（秒），当前为 604800（7 天）；
     * 后续请求需在 Header 中携带 {@code Authorization: Bearer <token>}。</p>
     *
     * @param request 登录请求体（已通过 @Valid 校验）
     * @return 统一响应壳，data 为 token 及用户基本信息
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 用户不存在/密码错误由服务层抛 1002/1003，此处直接透传成功结果
        LoginResponse response = authService.login(request);
        return ApiResponse.ok(response);
    }
}
