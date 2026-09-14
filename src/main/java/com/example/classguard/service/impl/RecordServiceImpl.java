package com.example.classguard.service.impl;

import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.DeviceRecord;
import com.example.classguard.mapper.DeviceRecordMapper;
import com.example.classguard.service.RecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备执行历史业务实现（API-07；MQTT cmd_resp 幂等入库）。
 */
@Slf4j
@Service
public class RecordServiceImpl implements RecordService {

    @Resource
    private DeviceRecordMapper deviceRecordMapper;

    @Override
    public PageResult<DeviceRecord> listRecords(int page, int size, LocalDateTime from, LocalDateTime to) {
        // TODO: ① size 上限 100；② selectCount + selectPage；③ PageResult.of 组装
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public void saveFromMqtt(DeviceRecord record) {
        // TODO: insertIgnore（唯一索引 uk_cmd_id + INSERT IGNORE，重复回执自动跳过，获得数据库级幂等）
        throw new UnsupportedOperationException("待实现");
    }
}
