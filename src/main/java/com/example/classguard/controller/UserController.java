package com.example.classguard.controller;

import com.example.classguard.config.JwtInterceptor;
import com.example.classguard.dto.ApiResponse;
import com.example.classguard.dto.ChangePasswordRequest;
import com.example.classguard.dto.UserInfoVO;
import com.example.classguard.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（REST 文档 API-03/API-04，登录用户，经 JwtInterceptor 校验 token）。
 * <p>当前用户标识（userId/username/role）由拦截器解析 JWT 后放入 request attribute，Controller 直接读取。
 * 业务错误码：1003 原密码错误（服务层抛出），经全局异常处理器统一转换，Controller 不 try/catch。</p>
 *
 * @author 第8组生产实习
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /** 用户业务逻辑（改密/查询个人信息），由 Spring 构造器注入。 */
    private final UserService userService;

    /**
     * 构造器注入 UserService。
     *
     * @param userService 用户服务实现
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 从请求属性中取当前登录用户 ID（JwtInterceptor 解析 JWT 后写入 ATTR_USER_ID）。
     *
     * @param request 当前 HTTP 请求
     * @return 当前用户 ID
     */
    private long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
    }

    /**
     * 从请求属性中取当前登录用户名（JwtInterceptor 解析 JWT 后写入 ATTR_USERNAME）。
     *
     * @param request 当前 HTTP 请求
     * @return 当前登录用户名
     */
    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
    }

    /**
     * API-03 修改密码：POST /api/user/password（登录用户）。
     *
     * <p>权限：任意已登录用户（仅能修改本人密码，userId 取自 JWT，不可伪造）。<br>
     * 请求体：{@link ChangePasswordRequest}（oldPassword/newPassword 校验注解已内置，@Valid 触发），
     * 校验失败由全局异常处理器转 code=1001。<br>
     * 业务错误码：1003 原密码错误（服务层抛出）。<br>
     * 边界说明：本期不做 token 吊销——改密成功后旧 token 在有效期内（604800 秒）仍可用，
     * 属于 REST v1.1 API-03 冻结契约的已知取舍。</p>
     *
     * @param request      修改密码请求体（已通过 @Valid 校验）
     * @param httpRequest  当前 HTTP 请求，用于从 request attribute 读取当前用户 ID
     * @return 统一响应壳，code=0 表示修改成功，data 为空
     */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        // userId 必须取自 JWT（拦截器写入），绝不能取自请求参数，防止越权改密
        userService.changePassword(currentUserId(httpRequest), request);
        return ApiResponse.ok();
    }

    /**
     * API-04 当前用户信息：GET /api/user/me（登录用户）。
     *
     * <p>权限：任意已登录用户。userId 取自 JWT。<br>
     * 无业务错误码（拦截器未放行的请求到不了本方法）。<br>
     * 成功时 data 返回 {@link UserInfoVO}：{@code {userId, username, role, createdAt}}。</p>
     *
     * @param httpRequest 当前 HTTP 请求，用于从 request attribute 读取当前用户 ID
     * @return 统一响应壳，data 为当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me(HttpServletRequest httpRequest) {
        // 由服务层按 userId 查询并组装 VO
        return ApiResponse.ok(userService.getCurrentUser(currentUserId(httpRequest)));
    }
}
