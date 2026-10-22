package com.example.classguard.service.impl;

import com.example.classguard.dto.ChangePasswordRequest;
import com.example.classguard.dto.UserInfoVO;
import com.example.classguard.entity.User;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.UserMapper;
import com.example.classguard.service.UserService;
import com.example.classguard.util.PasswordUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户业务实现（API-03 修改密码 / API-04 当前用户信息）。
 * <p>契约依据：《REST 接口文档 v1.1》API-03~API-04；
 * 《数据库设计说明书 V0.3》§4.1（密码 BCrypt 加盐哈希存储，禁止明文）。
 * <p>错误码契约：
 * <ul>
 *   <li>API-03 修改密码：原密码错误 → {@link ErrorCode#PASSWORD_ERROR}（1003）；
 *       用户不存在 → {@link ErrorCode#USER_NOT_FOUND}（1002，防御式处理）</li>
 *   <li>API-04 当前用户信息：用户不存在 → {@link ErrorCode#USER_NOT_FOUND}（1002）</li>
 * </ul>
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 用户表数据访问（users） */
    @Resource
    private UserMapper userMapper;

    /** BCrypt 密码工具（encode 加盐哈希 / matches 校验） */
    @Resource
    private PasswordUtil passwordUtil;

    /**
     * 修改密码（API-03）。
     * <p>流程：按 userId 查用户（为 null 抛 1002，token 校验后正常不会出现，防御式处理）
     * → BCrypt 校验原密码（错误抛 1003）→ 新密码 BCrypt 后 UPDATE。
     * <p>错误码契约：
     * <ul>
     *   <li>用户不存在 → {@link ErrorCode#USER_NOT_FOUND}（1002）</li>
     *   <li>原密码错误 → {@link ErrorCode#PASSWORD_ERROR}（1003）</li>
     * </ul>
     *
     * @param userId  当前登录用户 id（由 token 解析，服务端可信来源）
     * @param request 修改密码请求（oldPassword / newPassword）
     * @throws BusinessException 1002 账号不存在；1003 原密码错误
     */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        // ① 按 id 查询当前用户：token 校验通过后正常不会为 null，此处防御式处理（1002）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // ② 校验原密码：与库中 BCrypt 哈希不匹配则返回 1003（原密码错误）
        if (!passwordUtil.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // ③ 新密码先 BCrypt 加盐哈希，再按主键 UPDATE（DB V0.3 §4.1 禁止明文入库）
        userMapper.updatePassword(userId, passwordUtil.encode(request.getNewPassword()));

        // ④ 记录修改成功日志（仅记 userId，不含任何新旧密码内容）
        log.info("用户修改密码成功：userId={}", userId);
    }

    /**
     * 查询当前用户信息（API-04）。
     * <p>流程：按 userId 查用户（为 null 抛 1002）→ 转换为 UserInfoVO 返回。
     * <p>错误码契约：用户不存在 → {@link ErrorCode#USER_NOT_FOUND}（1002）。
     *
     * @param userId 当前登录用户 id（由 token 解析，服务端可信来源）
     * @return 用户信息：userId / username / role / createdAt
     * @throws BusinessException 1002 账号不存在
     */
    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        // ① 按主键查询当前用户，不存在则返回 1002（账号不存在）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // ② 实体转 VO：只暴露 userId / username / role / createdAt，不回传密码字段
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
