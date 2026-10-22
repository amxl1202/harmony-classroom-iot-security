package com.example.classguard.controller;

import com.example.classguard.config.JwtInterceptor;
import com.example.classguard.dto.ApiResponse;
import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.Alarm;
import com.example.classguard.service.AlarmService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 告警接口（REST 文档 API-05/API-06，登录用户）。
 *
 * <p>时间参数格式为 {@code yyyy-MM-dd HH:mm:ss}（application.properties 已配置
 * spring.mvc.format.date-time，LocalDateTime 绑定与 Jackson 序列化均按此格式，
 * Controller 无需再加 @DateTimeFormat；格式错误由全局异常处理器转 code=1001）。</p>
 *
 * @author 第8组生产实习
 */
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    /** 告警业务逻辑（分页查询/标记已处理），由 Spring 构造器注入。 */
    private final AlarmService alarmService;

    /**
     * 构造器注入 AlarmService。
     *
     * @param alarmService 告警服务实现
     */
    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /**
     * 从请求属性中取当前登录用户名（JwtInterceptor 解析 JWT 后写入 ATTR_USERNAME）。
     *
     * @param request 当前 HTTP 请求
     * @return 当前登录用户名
     */
    private String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
    }

    /**
     * API-05 告警列表：GET /api/alarms（登录用户）。
     *
     * <p>权限：任意已登录用户。<br>
     * query 参数约定（REST v1.1 API-05）：</p>
     * <ul>
     *   <li>page：页码，默认 1；size：每页条数，默认 20、上限 100（服务层负责钳制/校验）；</li>
     *   <li>from/to：时间过滤 {@code yyyy-MM-dd HH:mm:ss}，闭区间，可只传其一，也可都不传；</li>
     *   <li>status：0=未处理、1=已处理，不传=全部。</li>
     * </ul>
     * <p>排序固定 createdAt 倒序（最新告警在前）。<br>
     * 业务错误码：无专属业务码，参数格式错误由全局异常处理器转 1001。<br>
     * 成功时 data 返回 {@link PageResult}&lt;{@link Alarm}&gt; 分页结构。</p>
     *
     * @param page   页码（默认 1）
     * @param size   每页条数（默认 20，上限 100）
     * @param from   起始时间（可选，闭区间，格式 yyyy-MM-dd HH:mm:ss）
     * @param to     截止时间（可选，闭区间，可只传 from 或 to 之一）
     * @param status 处理状态（可选：0 未处理 / 1 已处理，不传=全部）
     * @return 统一响应壳，data 为告警分页结果（createdAt 倒序）
     */
    @GetMapping
    public ApiResponse<PageResult<Alarm>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) Integer status) {
        // 过滤/分页/排序逻辑全部下沉服务层，Controller 只做参数透传
        return ApiResponse.ok(alarmService.listAlarms(page, size, from, to, status));
    }

    /**
     * API-06 告警标记已处理：POST /api/alarms/{id}/handle（登录用户）。
     *
     * <p>权限：任意已登录用户。handledBy 取当前登录用户名（JWT 解析结果）。<br>
     * 路径参数 id 为数据库自增主键。<br>
     * 业务错误码：3001 告警不存在（服务层抛出）；幂等性——对已处理的告警重复调用
     * 直接返回成功，不报错（REST v1.1 API-06 冻结契约）。</p>
     *
     * @param id          告警主键（路径参数）
     * @param httpRequest 当前 HTTP 请求，用于从 request attribute 读取当前用户名
     * @return 统一响应壳，code=0 表示标记成功，data 为空
     */
    @PostMapping("/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id, HttpServletRequest httpRequest) {
        // handledBy 记录操作人，取自 JWT 而非请求参数，保证审计可信
        String handledBy = currentUsername(httpRequest);
        alarmService.handleAlarm(id, handledBy);
        return ApiResponse.ok();
    }
}
