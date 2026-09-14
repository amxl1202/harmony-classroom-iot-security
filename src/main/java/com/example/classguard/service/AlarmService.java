package com.example.classguard.service;

import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.Alarm;

import java.time.LocalDateTime;

/**
 * 告警业务（API-05 告警列表 / API-06 标记已处理；同时承接 MQTT alarm 消息入库）。
 */
public interface AlarmService {

    /** API-05 告警分页列表：from/to 闭区间可只传其一，status 筛选（0/1，不传=全部），createdAt 倒序；size 上限 100 */
    PageResult<Alarm> listAlarms(int page, int size, LocalDateTime from, LocalDateTime to, Integer status);

    /** API-06 标记已处理：status=1、handled_by=当前用户名、handled_at=服务器当前时间；不存在 3001；对已处理告警幂等返回成功 */
    void handleAlarm(Long id, String handledBy);

    /** MQTT alarm 消息入库：按 alarm_id 去重（已存在则丢弃），created_at = 服务端接收时间 */
    void saveFromMqtt(com.example.classguard.entity.Alarm alarm);
}
