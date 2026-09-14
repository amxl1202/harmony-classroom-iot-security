package com.example.classguard.service;

import com.example.classguard.dto.ChangePasswordRequest;
import com.example.classguard.dto.UserInfoVO;

/**
 * 用户业务（API-03 修改密码 / API-04 当前用户信息）。
 */
public interface UserService {

    /** API-03 修改密码：原密码 BCrypt 校验（错误 1003）→ 新密码 BCrypt 后 UPDATE */
    void changePassword(Long userId, ChangePasswordRequest request);

    /** API-04 当前用户信息：返回 {userId, username, role, createdAt} */
    UserInfoVO getCurrentUser(Long userId);
}
