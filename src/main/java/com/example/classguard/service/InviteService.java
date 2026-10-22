package com.example.classguard.service;

import com.example.classguard.dto.InviteVO;
import com.example.classguard.dto.PageResult;

/**
 * 邀请码业务（REST 接口文档 v1.2 API-10/11/12，管理员专属接口的业务层）。
 *
 * <p>契约依据：《REST 接口文档 v1.2》API-10（生成）/API-11（分页列表）/API-12（作废）；
 * 《改进方案-邀请码注册》v1.1 §2/§3；《数据库设计说明书 V0.4》§4.5（invite_codes 表）。
 *
 * <p>管理员权限由 JwtInterceptor 对 /api/invites 路径统一校验（非 admin 抛 2003，到不了本层）；
 * 本层业务错误码：1001 参数错误 / 3003 邀请码不存在 / 5000 服务器内部错误，
 * 实现层抛出 BusinessException，由全局异常处理器统一转为 {code,message} JSON 响应。
 *
 * @author 第8组生产实习
 */
public interface InviteService {

    /**
     * API-10 生成邀请码：8 位随机大写字母/数字，expiresAt = 当前时间 + validDays × 24h，created_by 取自 JWT。
     *
     * <p>撞码兜底：uk_code 全局唯一，随机码极端情况下重复时按方案 §3"冲突重试"最多重试 5 次，
     * 仍失败抛 5000。
     *
     * @param validDays 有效期天数（1~30，越界抛 1001；Controller 已做 @Valid 与缺省 7，此处兜底）
     * @param createdBy 签发人用户名（取自 JWT，由 Controller 从 request attribute 读出传入）
     * @return InviteVO（API-10 响应 data 为 {id, code, expiresAt}，共享 VO 含全量字段属正常）
     * @throws BusinessException 1001 validDays 越界；5000 连续撞码 5 次仍失败
     */
    InviteVO generate(int validDays, String createdBy);

    /**
     * API-11 邀请码分页列表：createdAt 倒序；"有效"判定=status=0 且未到期，由前端按此展示。
     *
     * <p>分页约定（REST v1.2 §2）：page ≥ 1（默认 1），size 默认 20、上限 100，越界由服务层钳制。
     * 过期不改库中 status，"有效"由 status=0 且当前时间 &lt; expiresAt 判定。
     *
     * @param page 页码（默认 1）
     * @param size 每页条数（默认 20，上限 100）
     * @return 分页结果，列表元素为 InviteVO 全量字段 {id, code, status, expiresAt, createdBy, createdAt}
     */
    PageResult<InviteVO> listInvites(int page, int size);

    /**
     * API-12 作废：status 置 1；幂等（已作废重复调用直接成功）；id 不存在 3003。
     *
     * @param id 邀请码主键
     * @throws BusinessException 3003 邀请码不存在（面向已认证管理员）
     */
    void disable(Long id);
}
