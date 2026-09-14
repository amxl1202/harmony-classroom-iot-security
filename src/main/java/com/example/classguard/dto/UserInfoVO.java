package com.example.classguard.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API-04 当前用户信息响应 data（REST 文档 API-04）。
 */
@Data
public class UserInfoVO {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色：user / admin */
    private String role;

    /** 注册时间（yyyy-MM-dd HH:mm:ss，服务端时区） */
    private LocalDateTime createdAt;
}
