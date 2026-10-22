package com.example.classguard.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码实体，对应表 invite_codes（《数据库设计说明书》V0.4 §4.5）。
 * <p>支撑"邀请码注册"功能（《改进方案-邀请码注册》v1.1）涉及的接口：
 * <ul>
 *   <li>API-10：管理员生成邀请码（服务端生成 8 位随机码）</li>
 *   <li>API-11：管理员分页查询邀请码列表</li>
 *   <li>API-12：管理员按 id 作废邀请码（status 置 1）</li>
 *   <li>API-01：注册时校验邀请码（不存在 / 已过期 / 已作废统一按 1005 处理）</li>
 * </ul>
 */
@Data
public class InviteCode {

    /** 邀请码ID，自增主键 */
    private Long id;

    /** 邀请码，8 位随机大写字母/数字，服务端生成，唯一（uk_code） */
    private String code;

    /** 状态：0 有效 / 1 已作废（过期由 expires_at 懒校验判定，不改 status） */
    private Integer status;

    /** 失效时间 = created_at + validDays × 24h，到达后注册校验视为"已过期" */
    private LocalDateTime expiresAt;

    /** 签发人用户名（取自 JWT），逻辑关联 users.username */
    private String createdBy;

    /** 生成时间（服务端时间） */
    private LocalDateTime createdAt;
}
