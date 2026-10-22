package com.example.classguard.service.impl;

import com.example.classguard.dto.RegisterRequest;
import com.example.classguard.entity.InviteCode;
import com.example.classguard.entity.User;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.InviteCodeMapper;
import com.example.classguard.mapper.UserMapper;
import com.example.classguard.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 注册流程单元测试（REST v1.2 API-01）。
 *
 * <p>重点验证契约里被冻结的校验顺序：参数(1001，由 @Valid 承担) → 邀请码(1005) → 用户名唯一(1004)。
 * 顺序错会导致"用户名已存在"这一信息泄露给未认证用户，或反之让有效邀请码被无用校验拦截。
 *
 * <p>纯 Mockito 测试，不启动 Spring 上下文，不访问数据库。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private InviteCodeMapper inviteCodeMapper;

    @Mock
    private PasswordUtil passwordUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    /**
     * 构造一个合法的注册请求。
     *
     * @param username   用户名
     * @param inviteCode 邀请码
     * @return 请求体
     */
    private RegisterRequest request(String username, String inviteCode) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword("abc123");
        req.setInviteCode(inviteCode);
        return req;
    }

    /**
     * 构造一个邀请码实体。
     *
     * @param status    状态（0 有效 / 1 已作废）
     * @param expiresAt 失效时间
     * @return 邀请码实体
     */
    private InviteCode invite(int status, LocalDateTime expiresAt) {
        InviteCode code = new InviteCode();
        code.setId(1L);
        code.setCode("K7X4M2Q9");
        code.setStatus(status);
        code.setExpiresAt(expiresAt);
        code.setCreatedBy("admin");
        return code;
    }

    /**
     * 邀请码不存在：必须返回 1005，且不得触达用户表。
     */
    @Test
    @DisplayName("注册-邀请码不存在 → 1005")
    void registerWithMissingInviteCodeShouldFail1005() {
        when(inviteCodeMapper.selectByCode("BADCODE1")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request("user01", "BADCODE1")));
        assertEquals(ErrorCode.INVITE_CODE_INVALID.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    /**
     * 邀请码已过期：即使 status=0，只要 now >= expiresAt 也必须返回 1005（懒校验）。
     */
    @Test
    @DisplayName("注册-邀请码已过期 → 1005")
    void registerWithExpiredInviteCodeShouldFail1005() {
        when(inviteCodeMapper.selectByCode("K7X4M2Q9"))
                .thenReturn(invite(0, LocalDateTime.now().minusMinutes(1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request("user01", "K7X4M2Q9")));
        assertEquals(ErrorCode.INVITE_CODE_INVALID.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    /**
     * 邀请码已作废（status=1）：与不存在/过期统一返回 1005，不向未认证方暴露状态细节。
     */
    @Test
    @DisplayName("注册-邀请码已作废 → 1005")
    void registerWithDisabledInviteCodeShouldFail1005() {
        when(inviteCodeMapper.selectByCode("K7X4M2Q9"))
                .thenReturn(invite(1, LocalDateTime.now().plusDays(1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request("user01", "K7X4M2Q9")));
        assertEquals(ErrorCode.INVITE_CODE_INVALID.getCode(), ex.getCode());
    }

    /**
     * 邀请码有效但用户名已存在：返回 1004，且顺序在邀请码校验之后（先通过邀请码才查重）。
     */
    @Test
    @DisplayName("注册-用户名重复 → 1004")
    void registerWithExistingUsernameShouldFail1004() {
        when(inviteCodeMapper.selectByCode("K7X4M2Q9"))
                .thenReturn(invite(0, LocalDateTime.now().plusDays(1)));
        when(userMapper.selectByUsername("user01")).thenReturn(new User());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request("user01", "K7X4M2Q9")));
        assertEquals(ErrorCode.USER_EXISTS.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    /**
     * 正常注册：密码必须经 BCrypt 编码后落库（禁止明文），role=user，used_invite_code 记录来源邀请码。
     */
    @Test
    @DisplayName("注册成功-密码经 BCrypt 编码、role=user、记录所用邀请码")
    void registerSuccessShouldPersistEncodedPassword() {
        when(inviteCodeMapper.selectByCode("K7X4M2Q9"))
                .thenReturn(invite(0, LocalDateTime.now().plusDays(1)));
        when(userMapper.selectByUsername("user01")).thenReturn(null);
        when(passwordUtil.encode("abc123")).thenReturn("$2a$10$mockedHash");

        RegisterRequest req = request("user01", "K7X4M2Q9");
        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("user01", saved.getUsername());
        assertEquals("$2a$10$mockedHash", saved.getPassword(), "落库必须是 BCrypt 哈希而非明文");
        assertEquals("user", saved.getRole(), "注册默认普通用户");
        assertEquals("K7X4M2Q9", saved.getUsedInviteCode(), "必须记录注册所用邀请码（审计字段）");
    }

    /**
     * 并发兜底：insert 抛 DuplicateKeyException 时必须翻译为 1004，而不是漏成 5000。
     */
    @Test
    @DisplayName("注册-并发撞 uk_username → 1004 而非 5000")
    void registerConcurrentDuplicateShouldTranslateTo1004() {
        when(inviteCodeMapper.selectByCode("K7X4M2Q9"))
                .thenReturn(invite(0, LocalDateTime.now().plusDays(1)));
        when(userMapper.selectByUsername("user01")).thenReturn(null);
        when(passwordUtil.encode("abc123")).thenReturn("$2a$10$mockedHash");
        when(userMapper.insert(any(User.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_username"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request("user01", "K7X4M2Q9")));
        assertEquals(ErrorCode.USER_EXISTS.getCode(), ex.getCode());
    }

    /**
     * 补充校验：用户名格式（4~20 位）由 @Valid 负责返回 1001，
     * 服务层不再重复校验，因此 3 位用户名只要邀请码有效且不重复仍会落库。
     * 该用例锁定"校验职责分层"，避免后续有人在服务层重复实现导致错误码不一致。
     */
    @Test
    @DisplayName("注册-字段格式校验只在 @Valid 层，服务层不重复实现")
    void registerDoesNotDuplicateFieldValidation() {
        when(inviteCodeMapper.selectByCode("K7X4M2Q9"))
                .thenReturn(invite(0, LocalDateTime.now().plusDays(1)));
        when(userMapper.selectByUsername("abc")).thenReturn(null);
        when(passwordUtil.encode("abc123")).thenReturn("$2a$10$mockedHash");

        authService.register(request("abc", "K7X4M2Q9"));

        verify(userMapper).insert(any(User.class));
    }
}
