package com.example.classguard.controller;

import com.example.classguard.config.JwtInterceptor;
import com.example.classguard.dto.ApiResponse;
import com.example.classguard.dto.InviteCreateRequest;
import com.example.classguard.dto.InviteVO;
import com.example.classguard.dto.PageResult;
import com.example.classguard.service.InviteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邀请码管理接口（REST 接口文档 v1.2 API-10/11/12，管理员专属，非 admin 由 JwtInterceptor 返回 2003）。
 *
 * <p>权限：仅 admin；管理员权限校验由 JwtInterceptor 对 /api/invites 路径统一处理（抛 2003），
 * Controller 无需重复校验角色。管理入口为 APP 设备管理 Tab 管理员区块（《改进方案-邀请码注册》v1.1）。
 *
 * <p>当前管理员身份（签发人 createdBy）通过 request attribute 获取：
 * JwtInterceptor 解析 JWT 后写入 ATTR_USERNAME，Controller 读取即可，无需二次解析 token。
 * 业务错误（1001 参数错误 / 3003 邀请码不存在 / 5000 服务器内部错误）由 InviteService 抛出
 * BusinessException，经全局异常处理器统一转换为 ApiResponse 错误壳，Controller 内不做 try/catch。</p>
 *
 * @author 第8组生产实习
 */
@RestController
@RequestMapping("/api/invites")
public class InviteController {

    /** 邀请码业务逻辑（生成/分页/作废），由 Spring 构造器注入。 */
    private final InviteService inviteService;

    /**
     * 构造器注入 InviteService。
     *
     * @param inviteService 邀请码服务实现
     */
    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    /**
     * 获取当前登录管理员用户名（作为邀请码 created_by 签发人）。
     *
     * @param request 当前请求（携带拦截器写入的 JWT 解析结果）
     * @return 管理员用户名
     */
    private String currentUsername(HttpServletRequest request) {
        // 拦截器解析 JWT 后写入 request attribute（JwtInterceptor.ATTR_USERNAME），此处直接读取
        return (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
    }

    /**
     * API-10 生成邀请码：POST /api/invites（管理员专属）。
     *
     * <p>权限：仅 admin；非 admin 请求被 JwtInterceptor 拦截并返回 code=2003，到不了本方法。<br>
     * 请求体可选：不传或传空对象时 validDays 取缺省值 7；显式传入时须为 1~30（@Valid 校验），
     * 越界由全局异常处理器转换为 code=1001（REST v1.2 API-10）。<br>
     * 邀请码由服务端生成：8 位随机大写字母/数字，expiresAt = 当前时间 + validDays × 24h。
     * 成功时 data 返回 {@link InviteVO}，以 {@code {id, code, expiresAt}} 为准（共享 VO 含全量字段）。<br>
     * 业务错误码：1001 validDays 越界；5000 撞码重试 5 次仍失败（均由服务层抛出）。</p>
     *
     * @param request      生成请求体（validDays 可选，缺省 7）
     * @param httpRequest  当前请求（用于读取 JWT 中的管理员用户名作为签发人）
     * @return 统一响应壳，data 为新邀请码 {id, code, expiresAt}
     */
    @PostMapping
    public ApiResponse<InviteVO> generate(@Valid @RequestBody InviteCreateRequest request,
                                          HttpServletRequest httpRequest) {
        // 缺省 7：请求体不传 validDays 时按 REST v1.2 API-10 约定取 7 天
        int validDays = request.getValidDays() == null ? 7 : request.getValidDays();
        return ApiResponse.ok(inviteService.generate(validDays, currentUsername(httpRequest)));
    }

    /**
     * API-11 邀请码分页列表：GET /api/invites（管理员专属）。
     *
     * <p>权限：仅 admin；非 admin 请求被 JwtInterceptor 拦截并返回 code=2003，到不了本方法。<br>
     * 分页约定（REST v1.2 §2）：page 默认 1；size 默认 20、上限 100，越界由服务层钳制不报错。<br>
     * 排序固定 createdAt 倒序。成功时 data 返回 {@link PageResult}&lt;{@link InviteVO}&gt;，
     * 列表元素为 {@code {id, code, status, expiresAt, createdBy, createdAt}}；
     * "有效"判定 = status=0 且当前时间 &lt; expiresAt（过期不改库中 status，由前端按此规则展示）。
     * 无专属业务错误码。</p>
     *
     * @param page 页码（默认 1）
     * @param size 每页条数（默认 20，上限 100）
     * @return 统一响应壳，data 为邀请码分页结果（createdAt 倒序）
     */
    @GetMapping
    public ApiResponse<PageResult<InviteVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // admin 角色校验已在 JwtInterceptor 完成，这里只做参数透传
        return ApiResponse.ok(inviteService.listInvites(page, size));
    }

    /**
     * API-12 作废邀请码：POST /api/invites/{id}/disable（管理员专属）。
     *
     * <p>权限：仅 admin；非 admin 请求被 JwtInterceptor 拦截并返回 code=2003，到不了本方法。<br>
     * 幂等：已作废（status=1）的邀请码重复调用直接成功（REST v1.2 API-12 幂等说明）；
     * id 不存在返回 code=3003 邀请码不存在（均由服务层处理/抛出）。<br>
     * 作废即 status 置 1；对已过期邀请码作废同样返回成功（过期本就不影响注册，status 仅表示作废标记）。</p>
     *
     * @param id 邀请码主键
     * @return 统一响应壳，code=0 表示作废成功（含幂等重复作废），data 为空
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        // 3003 不存在 / 已作废幂等成功，均由服务层处理，此处直接透传成功结果
        inviteService.disable(id);
        return ApiResponse.ok();
    }
}
