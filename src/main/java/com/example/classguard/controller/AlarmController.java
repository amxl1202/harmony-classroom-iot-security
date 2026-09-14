package com.example.classguard.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警接口（REST 文档 API-05/API-06，登录用户）。
 */
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    // TODO API-05 告警列表：GET /api/alarms
    //       query：page/size（默认 1/20、上限 100）、from/to（yyyy-MM-dd HH:mm:ss，闭区间可只传其一）、
    //       status（0 未处理 / 1 已处理，不传=全部）；
    //       调用 AlarmService.listAlarms，data 返回 PageResult<Alarm>（createdAt 倒序）

    // TODO API-06 告警标记已处理：POST /api/alarms/{id}/handle
    //       路径参数 id 为数据库自增主键；handledBy 取当前登录用户名；
    //       调用 AlarmService.handleAlarm（幂等；不存在 3001），返回 ApiResponse.ok()
}
