package com.example.classguard.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体，对应表 users（见《数据库设计说明书（第8组）》V0.2 §4.1）。
 * <p>role 取值：admin（预置管理员）/ user（注册默认）。
 */
@Data
public class User {

    /** 用户ID，自增主键 */
    private Long id;

    /** 用户名，唯一（uk_username），登录凭据，4~20 位字母/数字/下划线 */
    private String username;

    /** 密码，BCrypt 加盐哈希（VARCHAR(100)），禁止明文 */
    private String password;

    /** 角色：admin / user */
    private String role;

    /** 注册所用邀请码，逻辑关联 invite_codes.code；预置管理员为 NULL（DB V0.4 §4.1） */
    private String usedInviteCode;

    /** 注册时间（服务端时间） */
    private LocalDateTime createdAt;
}
