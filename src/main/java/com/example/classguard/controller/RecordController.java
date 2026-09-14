package com.example.classguard.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备执行历史接口（REST 文档 API-07，管理员专属，非 admin 返回 2003）。
 */
@RestController
@RequestMapping("/api/records")
public class RecordController {

    // TODO API-07 执行历史：GET /api/records
    //       query：page/size、from/to（同 API-05 规则）；调用 RecordService.listRecords，
    //       data 返回 PageResult<DeviceRecord>（createdAt 倒序）
    //       注：管理员权限校验由 JwtInterceptor 对 /api/records、/api/devices 路径统一处理
}
