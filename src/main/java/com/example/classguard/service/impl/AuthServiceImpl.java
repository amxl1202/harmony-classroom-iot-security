package com.example.classguard.service.impl;

import com.example.classguard.config.JwtUtil;
import com.example.classguard.dto.LoginRequest;
import com.example.classguard.dto.LoginResponse;
import com.example.classguard.dto.RegisterRequest;
import com.example.classguard.entity.InviteCode;
import com.example.classguard.entity.User;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.InviteCodeMapper;
import com.example.classguard.mapper.UserMapper;
import com.example.classguard.service.AuthService;
import com.example.classguard.util.PasswordUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证业务实现（API-01 注册 / API-02 登录，均为开放接口）。
 * <p>契约依据：《REST 接口文档 v1.2》API-01~API-02（v1.2 起注册受邀请码约束，请求体必填 inviteCode）；
 * 《数据库设计说明书 V0.4》§4.1（密码 BCrypt 加盐哈希存储，禁止明文；users.used_invite_code 审计字段）、
 * §4.5（invite_codes 表与过期懒校验）。
 * <p>错误码契约（API-01 校验顺序：1001 → 1005 → 1004）：
 * <ul>
 *   <li>API-01 注册：参数非法 → 1001（@Valid 已做）；邀请码无效（不存在/已过期/已作废统一）
 *       → {@link ErrorCode#INVITE_CODE_INVALID}（1005，先校验，不向未认证方暴露码状态细节）；
 *       用户名重复 → {@link ErrorCode#USER_EXISTS}（1004 账号已存在，后校验）</li>
 *   <li>API-02 登录：账号不存在 → {@link ErrorCode#USER_NOT_FOUND}（1002）；
 *       密码错误 → {@link ErrorCode#PASSWORD_ERROR}（1003）</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 用户表数据访问（users） */
    @Resource
    private UserMapper userMapper;

    /** 邀请码表数据访问（invite_codes，API-01 注册邀请码懒校验使用） */
    @Resource
    private InviteCodeMapper inviteCodeMapper;

    /** BCrypt 密码工具（encode 加盐哈希 / matches 校验） */
    @Resource
    private PasswordUtil passwordUtil;

    /** JWT 工具（generate 签发 / getExpireSeconds 有效期秒数） */
    @Resource
    private JwtUtil jwtUtil;

    /**
     * 注册（API-01）。
     * <p>契约：《REST 接口文档 v1.2》API-01，校验顺序（冻结契约）：
     * 参数校验 1001（@Valid 已做）→ 邀请码懒校验 1005（先校验，不向未认证方暴露码状态细节）
     * → 用户名唯一 1004 → BCrypt 入库（含 used_invite_code 审计关联）。
     * <p>邀请码"懒校验"：注册时实时判定 NOW() &lt; expires_at，不依赖定时任务（DB 设计 V0.4 §4.5）；
     * 不存在 / 已作废（status≠0）/ 已过期（expiresAt 不晚于当前时间）三种情况统一抛 1005 邀请码无效。
     * <p>错误码契约：1005 邀请码无效 → {@link ErrorCode#INVITE_CODE_INVALID}；
     * 1004 用户名已存在 → {@link ErrorCode#USER_EXISTS}。
     *
     * @param request 注册请求（username / password / inviteCode，均必填）
     * @throws BusinessException 1005 邀请码无效；1004 用户名已存在
     */
    @Override
    public void register(RegisterRequest request) {
        // ① 邀请码懒校验（API-01 v1.2，置于最前）：不存在 / 已作废 / 已过期统一 1005，
        //    不向未认证方暴露邀请码状态细节（方案 §2）；
        //    懒校验 NOW() < expires_at，不依赖定时任务，对齐设计 V0.4 §4.5
        InviteCode invite = inviteCodeMapper.selectByCode(request.getInviteCode());
        if (invite == null || invite.getStatus() == null || invite.getStatus() != 0
                || !LocalDateTime.now().isBefore(invite.getExpiresAt())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }

        // ② 用户名唯一校验：已存在同名账号则拒绝注册（API-01：重名返回 1004，顺序在邀请码校验之后）
        User existing = userMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        // ③ 组装用户实体：BCrypt 加盐哈希存储密码（DB V0.4 §4.1 禁止明文），注册默认为普通用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordUtil.encode(request.getPassword()));
        user.setRole("user");
        // 审计关联：记录注册所用邀请码（方案 §2），逻辑关联 invite_codes.code
        user.setUsedInviteCode(invite.getCode());

        // ④ 插入 users 表（username/password/role/used_invite_code，useGeneratedKeys 回填自增 id）；
        //    并发场景下两个同名请求可能都通过 ② 的唯一性查询，此时由 uk_username 兜底，
        //    必须把唯一键冲突翻译成契约错误码 1004，否则会漏成 5000（与 REST API-01 不符）
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("并发注册触发 uk_username 唯一索引冲突，按账号已存在处理：username={}", request.getUsername());
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        // ⑤ 记录注册成功日志（只记用户名/分配 id/所用邀请码，不含密码等敏感信息）
        log.info("用户注册成功：username={}, userId={}, usedInviteCode={}",
                user.getUsername(), user.getId(), user.getUsedInviteCode());
    }

    /**
     * 登录（API-02）。
     * <p>流程：按用户名查用户（不存在抛 1002）→ BCrypt 校验密码（错误抛 1003）
     * → 签发 JWT（载荷 userId/username/role）→ 返回 {token, username, role, expiresIn}。
     * <p>错误码契约：
     * <ul>
     *   <li>账号不存在 → {@link ErrorCode#USER_NOT_FOUND}（1002）</li>
     *   <li>密码错误 → {@link ErrorCode#PASSWORD_ERROR}（1003）</li>
     * </ul>
     *
     * @param request 登录请求（username / password）
     * @return 登录响应：token / username / role / expiresIn（604800 秒，即 7 天）
     * @throws BusinessException 1002 账号不存在；1003 密码错误
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // ① 按用户名查询用户，不存在则返回 1002（账号不存在）
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // ② BCrypt 校验密码：原始密码与库中哈希不匹配则返回 1003（密码错误）
        if (!passwordUtil.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // ③ 签发 JWT：载荷为 userId / username / role，有效期由 JwtUtil 配置决定
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());

        // ④ 组装响应：expiresIn 为 token 有效期秒数（REST 文档 API-02 约定 604800）
        LoginResponse response = new LoginResponse(token, user.getUsername(), user.getRole(), jwtUtil.getExpireSeconds());
        log.info("用户登录成功：username={}, userId={}", user.getUsername(), user.getId());
        return response;
    }
}
