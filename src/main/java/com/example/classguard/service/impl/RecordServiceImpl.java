package com.example.classguard.service.impl;

import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.DeviceRecord;
import com.example.classguard.mapper.DeviceRecordMapper;
import com.example.classguard.service.RecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备执行历史业务实现（API-07 设备执行历史分页，管理员；同时承接 MQTT cmd_resp 回执幂等入库）。
 * <p>契约依据：《REST 接口文档 v1.1》API-07 及 §2 分页约定；
 * 《数据库设计说明书 V0.3》§4.3（device_records 表结构）、§6.3(7)（cmd_resp 回执入库，唯一索引 uk_cmd_id）。
 * <p>权限契约：API-07 仅管理员可访问（鉴权由 Controller 层完成，本层不重复校验）。
 */
@Slf4j
@Service
public class RecordServiceImpl implements RecordService {

    /** 设备执行记录表数据访问（device_records，DB V0.3 §4.3） */
    @Resource
    private DeviceRecordMapper deviceRecordMapper;

    /**
     * 设备执行历史分页列表（API-07，管理员）。
     * <p>契约依据：《REST 接口文档 v1.1》API-07 及 §2 分页约定。
     * <ul>
     *   <li>分页参数钳制（REST §2）：page ≥ 1（默认 1）；size 默认 20、上限 100</li>
     *   <li>from/to：created_at 闭区间过滤，可只传其一，null 表示不限制</li>
     *   <li>排序 created_at DESC, id DESC 由 SQL（DeviceRecordMapper.xml）统一保证</li>
     * </ul>
     *
     * @param page 页码（&lt;1 自动钳制为 1）
     * @param size 每页条数（&lt;1 钳制为 20，&gt;100 钳制为 100）
     * @param from 起始时间（闭区间，可 null）
     * @param to   截止时间（闭区间，可 null）
     * @return 分页结果（total / page / size / list）
     */
    @Override
    public PageResult<DeviceRecord> listRecords(int page, int size, LocalDateTime from, LocalDateTime to) {
        // ① 参数钳制（REST 文档 §2 分页约定：page≥1 默认 1；size 默认 20、上限 100）
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        } else if (size > 100) {
            size = 100;
        }

        // ② 偏移量计算：用 long 防止大页码下 (page-1)*size 溢出 int
        long offset = (long) (page - 1) * size;

        // ③ 先查总数、再查当前页数据：两者过滤条件（from/to 闭区间）完全一致
        long total = deviceRecordMapper.selectCount(from, to);
        List<DeviceRecord> list = deviceRecordMapper.selectPage(from, to, offset, size);

        // ④ 组装统一分页响应（REST 文档 §2 分页结构）
        return PageResult.of(total, page, size, list);
    }

    /**
     * MQTT cmd_resp 回执入库（MQTT v1.2 §9 → DB V0.3 §6.3(7)）。
     * <p>幂等契约：同一 cmd_id 仅首次入库生效（DB V0.3 §6.3 唯一索引 uk_cmd_id）。
     * 采用 INSERT IGNORE 写入：rows==1 正常入库；rows==0 表示该 cmd_id 已存在（重复回执 / 重复投递），
     * 由唯一索引自动跳过，直接忽略不报错，获得数据库级幂等。
     * <p>兜底：极端并发下 INSERT IGNORE 与唯一索引冲突同时发生时，
     * catch DuplicateKeyException 忽略，语义一致。
     *
     * @param record 从 MQTT cmd_resp 主题消息解析出的执行记录实体
     */
    @Override
    public void saveFromMqtt(DeviceRecord record) {
        try {
            // ① INSERT IGNORE：rows==1 正常入库；rows==0 说明同一 cmd_id 已入库（重复回执自动跳过）
            int rows = deviceRecordMapper.insertIgnore(record);
            if (rows == 1) {
                log.info("执行回执入库成功：cmdId={}, deviceId={}, classroomId={}",
                        record.getCmdId(), record.getDeviceId(), record.getClassroomId());
            } else {
                // uk_cmd_id 唯一索引幂等：同一 cmd_id 的重复回执，忽略
                log.debug("重复 cmd_id 回执，跳过入库：cmdId={}", record.getCmdId());
            }
        } catch (DuplicateKeyException e) {
            // ② 兜底：并发窗口下唯一索引 uk_cmd_id 冲突，语义与 INSERT IGNORE 一致，忽略
            log.warn("并发插入触发 uk_cmd_id 唯一索引冲突，忽略：cmdId={}", record.getCmdId());
        }
    }
}
