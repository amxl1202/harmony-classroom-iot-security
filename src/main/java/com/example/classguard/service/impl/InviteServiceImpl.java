package com.example.classguard.service.impl;

import com.example.classguard.dto.InviteVO;
import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.InviteCode;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.InviteCodeMapper;
import com.example.classguard.service.InviteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 邀请码业务实现（REST 接口文档 v1.2 API-10/11/12）。
 *
 * <p>契约依据：《REST 接口文档 v1.2》API-10/API-11/API-12；
 * 《改进方案-邀请码注册》v1.1 §2（8 位随机码、懒校验）、§3（冲突重试）；
 * 《数据库设计说明书 V0.4》§4.5（invite_codes：code 唯一 uk_code、status 0 有效/1 已作废、
 * expires_at = created_at + validDays × 24h）。
 *
 * <p>错误码契约：
 * <ul>
 *   <li>API-10：validDays 越界 → {@link ErrorCode#PARAM_ERROR}（1001）；
 *       撞码重试 5 次仍失败 → {@link ErrorCode#INTERNAL_ERROR}（5000）</li>
 *   <li>API-11：无专属业务错误码（分页参数越界按 REST §2 约定钳制，不报错）</li>
 *   <li>API-12：id 不存在 → {@link ErrorCode#INVITE_NOT_FOUND}（3003）；
 *       已作废重复调用幂等成功</li>
 * </ul>
 */
@Slf4j
@Service
public class InviteServiceImpl implements InviteService {

    /** 邀请码长度：8 位（方案 §2） */
    private static final int CODE_LENGTH = 8;

    /** 随机码字符集：8 位随机大写字母/数字（方案 §2） */
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** 撞码重试上限：uk_code 全局唯一，随机码极端重复时最多尝试 5 次（方案 §3"冲突重试"） */
    private static final int MAX_RETRY = 5;

    /** 安全随机源：保证邀请码不可预测 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 邀请码表数据访问（invite_codes，DB V0.4 §4.5） */
    @Resource
    private InviteCodeMapper inviteCodeMapper;

    /**
     * API-10 生成邀请码（REST v1.2 API-10）。
     *
     * <p>响应 data 为 {id, code, expiresAt}（共享 VO {@link InviteVO} 含全量字段，超出示例字段属正常）。
     *
     * @param validDays 有效期天数（1~30）
     * @param createdBy 签发人用户名（取自 JWT）
     * @return 邀请码视图对象
     * @throws BusinessException 1001 validDays 越界（兜底校验）；5000 连续撞码 5 次仍失败
     */
    @Override
    public InviteVO generate(int validDays, String createdBy) {
        // ① 防御校验：Controller 已做 @Valid 与缺省 7，此处兜底（越界返回 1001 参数错误）
        if (validDays < 1 || validDays > 30) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "validDays 须为 1~30");
        }

        // ② 循环最多 5 次：生成 8 位随机码并落库，uk_code 全局唯一冲突时重试（方案 §3"冲突重试"）
        InviteCode inviteCode = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            // 生成 8 位随机大写字母/数字（方案 §2）：SecureRandom 逐位从字符集取样
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
            }

            // 组装实体：status=0 有效；expires_at = created_at + validDays × 24h（DB V0.4 §4.5）；
            // created_by 记录签发管理员（取自 JWT），created_at 由数据库默认值生成
            inviteCode = new InviteCode();
            inviteCode.setCode(sb.toString());
            inviteCode.setStatus(0);
            inviteCode.setExpiresAt(LocalDateTime.now().plusDays(validDays));
            inviteCode.setCreatedBy(createdBy);

            try {
                // 落库（useGeneratedKeys 回填自增 id），成功即跳出重试循环
                inviteCodeMapper.insert(inviteCode);
                break;
            } catch (DuplicateKeyException e) {
                // uk_code 全局唯一冲突（撞码，概率极低）：告警后重试，不向上抛出
                log.warn("邀请码生成撞码，重试第{}次：code={}", attempt, inviteCode.getCode());
                inviteCode = null;
            }
        }

        // ③ 5 次仍冲突：按服务器内部错误处理，提示稍后重试（5000）
        if (inviteCode == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邀请码生成失败，请重试");
        }

        // 记录完整码便于演示/核对（方案无脱敏要求，照记）
        log.info("邀请码生成：code={}, validDays={}, createdBy={}, id={}",
                inviteCode.getCode(), validDays, createdBy, inviteCode.getId());

        // ④ 实体转 InviteVO（全量字段）返回；API-10 响应 data 以 {id, code, expiresAt} 为准
        InviteVO vo = new InviteVO();
        vo.setId(inviteCode.getId());
        vo.setCode(inviteCode.getCode());
        vo.setStatus(inviteCode.getStatus());
        vo.setExpiresAt(inviteCode.getExpiresAt());
        vo.setCreatedBy(inviteCode.getCreatedBy());
        vo.setCreatedAt(inviteCode.getCreatedAt());
        return vo;
    }

    /**
     * API-11 邀请码分页列表（REST v1.2 API-11）。
     *
     * <p>排序固定 createdAt 倒序（Mapper 层 ORDER BY created_at DESC, id DESC）。
     * status 过期不改动："有效" = status=0 且当前时间 &lt; expiresAt，由 expiresAt 判定，
     * 前端按此规则展示状态，服务端不做定时任务刷新。
     *
     * @param page 页码（默认 1）
     * @param size 每页条数（默认 20，上限 100）
     * @return 分页结果（total/page/size/list），列表元素含 InviteVO 全量字段
     */
    @Override
    public PageResult<InviteVO> listInvites(int page, int size) {
        // 分页参数钳制（REST v1.2 §2 分页约定）：page<1 → 1；size<1 → 20；size>100 → 100
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }

        // offset 用 long 防止 (page-1)*size 整型溢出（深翻页场景）
        long offset = (long) (page - 1) * size;

        // 先查总数，再查当前页记录，组装统一分页结构 {total,page,size,list}
        long total = inviteCodeMapper.selectCount();
        List<InviteVO> voList = inviteCodeMapper.selectPage(offset, size).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(total, page, size, voList);
    }

    /**
     * API-12 作废邀请码（REST v1.2 API-12）。
     *
     * <p>幂等：disable SQL 为 UPDATE ... SET status=1 WHERE id=#{id} AND status=0，
     * 已作废记录影响行数为 0，本方法视为成功直接返回（REST v1.2 API-12 幂等说明）。
     *
     * @param id 邀请码主键
     * @throws BusinessException 3003 邀请码不存在（面向已认证管理员）
     */
    @Override
    public void disable(Long id) {
        // ① 前置校验：目标记录不存在 → 3003（区分"不存在"与"已作废幂等"两种情形）
        InviteCode invite = inviteCodeMapper.selectById(id);
        if (invite == null) {
            throw new BusinessException(ErrorCode.INVITE_NOT_FOUND);
        }

        // ② 作废（WHERE status=0 保证幂等）：rows=1 本次作废成功；rows=0 早已作废，幂等成功
        int rows = inviteCodeMapper.disable(id);
        if (rows == 1) {
            log.info("邀请码已作废：code={}, id={}", invite.getCode(), id);
        } else {
            log.info("邀请码已处于作废状态，幂等返回成功：code={}, id={}", invite.getCode(), id);
        }
    }

    /**
     * 实体转视图对象：全量字段复制（API-10/11 共用共享 VO，字段超出文档示例属正常）。
     *
     * @param inviteCode 邀请码实体
     * @return 邀请码视图对象
     */
    private InviteVO toVO(InviteCode inviteCode) {
        InviteVO vo = new InviteVO();
        vo.setId(inviteCode.getId());
        vo.setCode(inviteCode.getCode());
        vo.setStatus(inviteCode.getStatus());
        vo.setExpiresAt(inviteCode.getExpiresAt());
        vo.setCreatedBy(inviteCode.getCreatedBy());
        vo.setCreatedAt(inviteCode.getCreatedAt());
        return vo;
    }
}
