package com.example.classguard.controller;

import com.example.classguard.dto.ApiResponse;
import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.DeviceRecord;
import com.example.classguard.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 设备执行历史接口（REST 文档 API-07，管理员专属，非 admin 返回 2003）。
 *
 * <p>管理员权限校验由 JwtInterceptor 对 /api/records 路径统一处理，Controller 无需重复校验角色。
 * 时间参数格式为 {@code yyyy-MM-dd HH:mm:ss}（application.properties 已配置
 * spring.mvc.format.date-time，LocalDateTime 绑定按此格式；格式错误由全局异常处理器转 1001）。</p>
 *
 * @author 第8组生产实习
 */
@RestController
@RequestMapping("/api/records")
public class RecordController {

    /** 执行历史业务逻辑（分页查询），由 Spring 构造器注入。 */
    private final RecordService recordService;

    /**
     * 构造器注入 RecordService。
     *
     * @param recordService 执行历史服务实现
     */
    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * API-07 执行历史：GET /api/records（管理员专属）。
     *
     * <p>权限：仅 admin；非 admin 请求被 JwtInterceptor 拦截并返回 code=2003，到不了本方法。</p>
     * <p>query 参数规则同 API-05（REST v1.1）：</p>
     * <ul>
     *   <li>page：页码，默认 1；size：每页条数，默认 20、上限 100（服务层负责钳制/校验）；</li>
     *   <li>from/to：时间过滤 {@code yyyy-MM-dd HH:mm:ss}，闭区间，可只传其一，也可都不传。</li>
     * </ul>
     * <p>排序固定 createdAt 倒序。无专属业务错误码。
     * 成功时 data 返回 {@link PageResult}&lt;{@link DeviceRecord}&gt; 分页结构。</p>
     *
     * @param page 页码（默认 1）
     * @param size 每页条数（默认 20，上限 100）
     * @param from 起始时间（可选，闭区间，格式 yyyy-MM-dd HH:mm:ss）
     * @param to   截止时间（可选，闭区间，可只传 from 或 to 之一）
     * @return 统一响应壳，data 为执行历史分页结果（createdAt 倒序）
     */
    @GetMapping
    public ApiResponse<PageResult<DeviceRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        // admin 角色校验已在 JwtInterceptor 完成，这里只做参数透传
        return ApiResponse.ok(recordService.listRecords(page, size, from, to));
    }
}
