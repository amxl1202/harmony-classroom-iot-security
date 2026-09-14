package com.example.classguard.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（REST 文档 API-03/API-04，登录用户，经 JwtInterceptor 校验 token）。
 * <p>当前用户标识（userId/username/role）由拦截器解析 JWT 后放入 request attribute，Controller 直接读取。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    // TODO API-03 修改密码：POST /api/user/password
    //       请求体 {@link com.example.classguard.dto.ChangePasswordRequest}（@Valid 校验），
    //       调用 UserService.changePassword(userId, request)，返回 ApiResponse.ok()

    // TODO API-04 当前用户信息：GET /api/user/me
    //       调用 UserService.getCurrentUser(userId)，data 返回 {userId, username, role, createdAt}
}
