package com.example.classguard.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（REST 文档 API-01/API-02，开放接口，无需 token）。
 */
@RestController
@RequestMapping("/api/auth")

public class AuthController {

    // TODO API-01 注册：POST /api/auth/register
    //       请求体 {@link com.example.classguard.dto.RegisterRequest}（@Valid 校验），
    //       调用 AuthService.register，返回 ApiResponse.ok()



    // TODO API-02 登录：POST /api/auth/login
    //       请求体 {@link com.example.classguard.dto.LoginRequest}（@Valid 校验），
    //       调用 AuthService.login，data 返回 {token, username, role, expiresIn}
}
