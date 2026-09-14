package com.example.classguard.service.impl;

import com.example.classguard.dto.LoginRequest;
import com.example.classguard.dto.LoginResponse;
import com.example.classguard.dto.RegisterRequest;
import com.example.classguard.entity.User;
import com.example.classguard.mapper.UserMapper;
import com.example.classguard.service.AuthService;
import com.example.classguard.util.PasswordUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证业务实现（API-01 / API-02）。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordUtil passwordUtil;

    @Override
    public void register(RegisterRequest request) {
        // TODO: ① userMapper.selectByUsername 唯一校验，已存在返回 1004；
        //       ② passwordUtil.encode 加密；③ userMapper.insert（role 取默认值 user）
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // TODO: ① selectByUsername，不存在返回 1002；
        //       ② passwordUtil.matches 校验，错误返回 1003；
        //       ③ JwtUtil.generate 签发 token（载荷 userId/username/role，有效期 604800 秒）并返回
        throw new UnsupportedOperationException("待实现");
    }
}
