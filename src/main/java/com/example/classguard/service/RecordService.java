package com.example.classguard.service;

import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.DeviceRecord;

import java.time.LocalDateTime;

/**
 * 设备执行历史业务（API-07，管理员；同时承接 MQTT cmd_resp 回执幂等入库）。
 */
public interface RecordService {

    /** API-07 执行历史分页：from/to 闭区间可只传其一，createdAt 倒序 */
    PageResult<DeviceRecord> listRecords(int page, int size, LocalDateTime from, LocalDateTime to);

    /** MQTT cmd_resp 回执入库：按 cmd_id 幂等（唯一索引 + INSERT IGNORE，同一 cmd_id 仅首次生效） */
    void saveFromMqtt(DeviceRecord record);
}
