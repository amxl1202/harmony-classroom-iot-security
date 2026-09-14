package com.example.classguard.service.impl;

import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.Alarm;
import com.example.classguard.mapper.AlarmMapper;
import com.example.classguard.service.AlarmService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警业务实现（API-05 / API-06；MQTT alarm 入库）。
 */
@Slf4j
@Service
public class AlarmServiceImpl implements AlarmService {

    @Resource
    private AlarmMapper alarmMapper;

    @Override
    public PageResult<Alarm> listAlarms(int page, int size, LocalDateTime from, LocalDateTime to, Integer status) {
        // TODO: ① size 上限 100（REST 文档 §5）；② selectCount + selectPage；③ PageResult.of 组装
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public void handleAlarm(Long id, String handledBy) {
        // TODO: ① selectById，不存在返回 3001；
        //       ② markHandled（WHERE status=0 保证幂等：已处理告警再次调用直接返回成功，不重复更新）
        throw new UnsupportedOperationException("待实现");
    }

    @Override
    public void saveFromMqtt(Alarm alarm) {
        // TODO: 先查 selectByAlarmId（alarm_id 已存在则丢弃本条消息）再 insert；
        //       created_at 由数据库默认值 CURRENT_TIMESTAMP 生成（服务端接收时间）
        throw new UnsupportedOperationException("待实现");
    }
}
