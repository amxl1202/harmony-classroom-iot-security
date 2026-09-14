package com.example.classguard.service.impl;

import com.example.classguard.dto.ChangePasswordRequest;
import com.example.classguard.dto.UserInfoVO;
import com.example.classguard.entity.User;
import com.example.classguard.mapper.UserMapper;
import com.example.classguard.service.UserService;
import com.example.classguard.util.PasswordUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户业务实现（API-03 / API-04）。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordUtil passwordUtil;

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        // TODO: ① selectById 后 passwordUtil.matches 校验原密码，错误返回 1003；
        //       ② 新密码 BCrypt 后 userMapper.updatePassword（API-03）
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        // TODO: selectById 转 UserInfoVO（API-04）
        throw new UnsupportedOperationException("待实现");
    }
}
