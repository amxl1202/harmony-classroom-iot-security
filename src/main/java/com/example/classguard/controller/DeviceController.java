package com.example.classguard.controller;

import com.example.classguard.config.JwtInterceptor;
import com.example.classguard.dto.ApiResponse;
import com.example.classguard.dto.CommandRequest;
import com.example.classguard.dto.CommandResponse;
import com.example.classguard.entity.Device;
import com.example.classguard.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备接口（REST 文档 API-08/API-09，管理员专属，非 admin 返回 2003）。
 *
 * <p>管理员权限校验由 JwtInterceptor 对 /api/devices 路径统一处理，Controller 无需重复校验角色。
 * 设备在线状态由 MQTT status/LWT（遗嘱消息）驱动。业务错误码：
 * 3002 设备离线或 3 秒内无回执（由服务层抛出 BusinessException，
 * 经全局异常处理器统一转换，Controller 不 try/catch）。</p>
 *
 * @author 第8组生产实习
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    /** 设备业务逻辑（列表/指令下发），由 Spring 构造器注入。 */
    private final DeviceService deviceService;

    /**
     * 构造器注入 DeviceService。
     *
     * @param deviceService 设备服务实现
     */
    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
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
     * API-08 设备列表与在线状态：GET /api/devices（管理员专属）。
     *
     * <p>权限：仅 admin；非 admin 请求被 JwtInterceptor 拦截并返回 code=2003，到不了本方法。</p>
     * <p>成功时 data 返回 {@link Device} 数组：
     * {@code [{classroomId, deviceId, deviceName, online, lastOnlineAt}]}。
     * 其中 online/lastOnlineAt 由 MQTT status/LWT（遗嘱消息）驱动更新
     * （REST v1.1 API-08）。无专属业务错误码。</p>
     *
     * @return 统一响应壳，data 为全部设备信息列表
     */
    @GetMapping
    public ApiResponse<List<Device>> list() {
        // 列表无分页与过滤参数，全量返回
        return ApiResponse.ok(deviceService.listDevices());
    }

    /**
     * API-09 设备指令下发：POST /api/devices/{deviceId}/command（管理员专属）。
     *
     * <p>权限：仅 admin；非 admin 请求被 JwtInterceptor 拦截并返回 code=2003。</p>
     * <p>请求体：{@link CommandRequest}（command+paras，校验注解已内置，@Valid 触发）。
     * issuedBy 取当前登录用户名（JWT），用于指令审计。</p>
     * <p>成功时 data 返回 {@link CommandResponse}：{@code {cmdId, result, reason, state}}。<br>
     * 业务错误码（REST v1.1 API-09 超时响应）：
     * 3002 设备离线或 3 秒内无回执——服务层抛 BusinessException(3002) 且异常 data
     * 携带 {@code {cmdId, ...}}，由全局异常处理器输出到响应体；3001 设备不存在。</p>
     *
     * @param deviceId    设备 ID（路径参数）
     * @param request     指令请求体（command + paras，已通过 @Valid 校验）
     * @param httpRequest 当前 HTTP 请求，用于从 request attribute 读取当前用户名
     * @return 统一响应壳，data 为指令执行结果
     */
    @PostMapping("/{deviceId}/command")
    public ApiResponse<CommandResponse> command(@PathVariable String deviceId,
                                                @Valid @RequestBody CommandRequest request,
                                                HttpServletRequest httpRequest) {
        // issuedBy 取自 JWT，保证指令下发审计可信
        String issuedBy = currentUsername(httpRequest);
        // 设备离线/3 秒无回执时服务层抛 BusinessException(3002)，data 携带 {cmdId,...}，
        // 由全局异常处理器统一输出，此处不做 try/catch
        return ApiResponse.ok(deviceService.sendCommand(deviceId, request.getCommand(),
                request.getParas(), issuedBy));
    }
}
