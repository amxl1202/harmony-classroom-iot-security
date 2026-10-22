package com.example.classguard.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码视图对象（REST v1.2，邀请码管理/生成接口共用）。
 * <ul>
 *   <li>API-10 生成响应：data 返回 {id, code, expiresAt}；本 VO 为共享 VO，含全量字段，
 *       服务层按需取用/序列化时以文档字段为准。</li>
 *   <li>API-11 列表：列表元素为 {id, code, status, expiresAt, createdBy, createdAt}。</li>
 * </ul>
 * <p>"有效"判定 = status=0 且当前时间 &lt; expiresAt，
 * 过期不改库中 status，由前端按此规则展示状态。
 */
@Data
public class InviteVO {

    /** 邀请码ID，自增主键 */
    private Long id;

    /** 邀请码，8 位随机大写字母/数字 */
    private String code;

    /** 状态：0 有效 / 1 已作废（过期由 expiresAt 懒校验判定，不改 status） */
    private Integer status;

    /** 失效时间 = createdAt + validDays × 24h */
    private LocalDateTime expiresAt;

    /** 签发人用户名（取自 JWT），逻辑关联 users.username */
    private String createdBy;

    /** 生成时间（服务端时间） */
    private LocalDateTime createdAt;
}
