package com.example.classguard.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备接口（REST 文档 API-08/API-09，管理员专属，非 admin 返回 2003）。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    // TODO API-08 设备列表与在线状态：GET /api/devices
    //       调用 DeviceService.listDevices，data 返回数组
    //       [{classroomId, deviceId, deviceName, online, lastOnlineAt}]

    // TODO API-09 设备指令下发：POST /api/devices/{deviceId}/command
    //       请求体 {@link com.example.classguard.dto.CommandRequest}（@Valid 校验）；
    //       issuedBy 取当前登录用户名（JWT），调用 DeviceService.sendCommand；
    //       成功 data 返回 {cmdId, result, reason, state}；超时/设备离线返回 code=3002
}
