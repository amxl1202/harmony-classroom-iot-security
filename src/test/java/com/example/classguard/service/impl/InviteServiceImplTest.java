package com.example.classguard.service.impl;

import com.example.classguard.dto.InviteVO;
import com.example.classguard.entity.InviteCode;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.InviteCodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InviteServiceImpl 单元测试（REST v1.2 API-10 / 改进方案-邀请码注册 v1.1）。
 *
 * <p>覆盖：有效天数越界（1001）、正常生成的码格式与失效时间、uk_code 撞码重试（最多 5 次）、
 * 连续撞码 5 次返回 5000。撞码重试是"服务端生成随机码"这套方案里唯一需要兜底的失败路径，
 * 也是最容易被漏测的分支。
 *
 * <p>纯 Mockito 测试，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class InviteServiceImplTest {

    @Mock
    private InviteCodeMapper inviteCodeMapper;

    @InjectMocks
    private InviteServiceImpl inviteService;

    /**
     * 越界校验：validDays 必须为 1~30，0 与 31 均返回 1001，且不得写库。
     */
    @Test
    @DisplayName("生成-validDays 越界 → 1001")
    void generateWithIllegalValidDaysShouldFail1001() {
        BusinessException low = assertThrows(BusinessException.class,
                () -> inviteService.generate(0, "admin"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), low.getCode());

        BusinessException high = assertThrows(BusinessException.class,
                () -> inviteService.generate(31, "admin"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), high.getCode());
    }

    /**
     * 正常生成：8 位大写字母/数字、status=0、expiresAt ≈ now + validDays 天、createdBy 取自 JWT。
     */
    @Test
    @DisplayName("生成成功-8 位码、status=0、失效时间=now+validDays 天")
    void generateShouldBuildCorrectInviteCode() {
        when(inviteCodeMapper.insert(any(InviteCode.class))).thenReturn(1);

        LocalDateTime before = LocalDateTime.now().plusDays(7);
        InviteVO vo = inviteService.generate(7, "admin");
        LocalDateTime after = LocalDateTime.now().plusDays(7);

        assertNotNull(vo.getCode());
        assertEquals(8, vo.getCode().length(), "邀请码固定 8 位");
        assertTrue(vo.getCode().matches("[A-Z0-9]{8}"), "邀请码只含大写字母与数字，实际：" + vo.getCode());
        assertEquals(0, vo.getStatus(), "新建邀请码 status=0 有效");
        assertEquals("admin", vo.getCreatedBy());

        // expiresAt 应落在 [before, after] 之间（允许数百毫秒的执行误差）
        assertTrue(!vo.getExpiresAt().isBefore(before) && !vo.getExpiresAt().isAfter(after),
                "expiresAt 应为当前时间 + 7 天，实际：" + vo.getExpiresAt());

        ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
        verify(inviteCodeMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    /**
     * 撞码重试：前 4 次 insert 抛 uk_code 冲突，第 5 次成功 —— 不应抛出异常，
     * 且应恰好尝试 5 次（MAX_RETRY 上限）。
     */
    @Test
    @DisplayName("生成-前 4 次撞码、第 5 次成功 → 正常返回")
    void generateShouldRetryOnDuplicateCode() {
        when(inviteCodeMapper.insert(any(InviteCode.class)))
                .thenThrow(new DuplicateKeyException("uk_code"))
                .thenThrow(new DuplicateKeyException("uk_code"))
                .thenThrow(new DuplicateKeyException("uk_code"))
                .thenThrow(new DuplicateKeyException("uk_code"))
                .thenReturn(1);

        InviteVO vo = inviteService.generate(7, "admin");

        assertNotNull(vo.getCode());
        verify(inviteCodeMapper, times(5)).insert(any(InviteCode.class));
    }

    /**
     * 连续撞码 5 次：按服务器内部错误返回 5000，提示稍后重试。
     */
    @Test
    @DisplayName("生成-连续 5 次撞码 → 5000")
    void generateShouldFail5000AfterMaxRetry() {
        when(inviteCodeMapper.insert(any(InviteCode.class)))
                .thenThrow(new DuplicateKeyException("uk_code"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteService.generate(7, "admin"));
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());
        verify(inviteCodeMapper, times(5)).insert(any(InviteCode.class));
    }
}
