package com.example.classguard.service.impl;

import com.example.classguard.dto.PageResult;
import com.example.classguard.entity.Alarm;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.AlarmMapper;
import com.example.classguard.service.AlarmService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警业务实现（API-05 告警列表 / API-06 标记已处理；同时承接 MQTT alarm 消息入库）。
 * <p>契约依据：《REST 接口文档 v1.1》API-05 / API-06 及 §2 分页约定；
 * 《数据库设计说明书 V0.3》§4.2（alarms 表结构）、§6.2（MQTT alarm 入库）、§6.3（唯一索引 uk_alarm_id）。
 * <p>错误码契约：
 * <ul>
 *   <li>API-06 标记已处理：告警不存在 → {@link ErrorCode#ALARM_NOT_FOUND}（3001）；
 *       对已处理告警重复调用为幂等语义，直接返回成功不报错（REST API-06 幂等说明）</li>
 * </ul>
 */
@Slf4j
@Service
public class AlarmServiceImpl implements AlarmService {

    /** 告警判重时间窗口（秒）：只把同一窗口内的重复投递判为重复消息（DB V0.5） */
    private static final int ALARM_DEDUP_WINDOW_SECOND = 10;

    /** 告警表数据访问（alarms，DB V0.3 §4.2） */
    @Resource
    private AlarmMapper alarmMapper;

    /**
     * 告警分页列表（API-05，登录用户均可查询）。
     * <p>契约依据：《REST 接口文档 v1.1》API-05 及 §2 分页约定。
     * <ul>
     *   <li>分页参数钳制（REST §2）：page ≥ 1（默认 1）；size 默认 20、上限 100</li>
     *   <li>from/to：created_at 闭区间过滤，可只传其一，null 表示不限制</li>
     *   <li>status：筛选告警状态（0=未处理 / 1=已处理），不传 = 全部</li>
     *   <li>排序 created_at DESC, id DESC 由 SQL（AlarmMapper.xml）统一保证</li>
     * </ul>
     *
     * @param page   页码（&lt;1 自动钳制为 1）
     * @param size   每页条数（&lt;1 钳制为 20，&gt;100 钳制为 100）
     * @param from   起始时间（闭区间，可 null）
     * @param to     截止时间（闭区间，可 null）
     * @param status 状态筛选（0/1，可 null 表示全部）
     * @return 分页结果（total / page / size / list）
     */
    @Override
    public PageResult<Alarm> listAlarms(int page, int size, LocalDateTime from, LocalDateTime to, Integer status) {
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

        // ③ 先查总数、再查当前页数据：两者过滤条件（from/to 闭区间、status）完全一致
        long total = alarmMapper.selectCount(from, to, status);
        List<Alarm> list = alarmMapper.selectPage(from, to, status, offset, size);

        // ④ 组装统一分页响应（REST 文档 §2 分页结构）
        return PageResult.of(total, page, size, list);
    }

    /**
     * 标记告警已处理（API-06）。
     * <p>契约依据：《REST 接口文档 v1.1》API-06；DB V0.3 §4.2（status / handled_by / handled_at）。
     * <p>语义：status 置 1、handled_by 记操作人、handled_at 记服务器当前时间（由 SQL NOW() 生成）。
     * 幂等说明：markHandled 的 SQL 带 WHERE status=0，对已处理告警（rows==0）不重复更新，
     * 直接视为成功返回——重复调用不报错、不覆盖首次处理人（REST API-06 幂等说明）。
     * <p>错误码契约：告警不存在 → {@link ErrorCode#ALARM_NOT_FOUND}（3001 告警不存在）。
     *
     * @param id        告警主键 id
     * @param handledBy 处理人（当前登录用户名，由 Controller 从 token 解析后传入）
     * @throws BusinessException 3001 告警不存在
     */
    @Override
    public void handleAlarm(Long id, String handledBy) {
        // ① 前置校验：告警不存在 → 3001（REST API-06 错误码契约）
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BusinessException(ErrorCode.ALARM_NOT_FOUND);
        }

        // ② 标记已处理：rows==1 正常更新；rows==0 说明该告警已是已处理状态（WHERE status=0 不命中）
        int rows = alarmMapper.markHandled(id, handledBy);
        if (rows == 1) {
            // 正常路径：首次标记成功，记录处理人与告警 id，便于审计追溯
            log.info("告警已标记处理：alarmId={}, handledBy={}", alarm.getAlarmId(), handledBy);
        } else {
            // 幂等路径：告警已被处理过，直接返回成功，不抛错（REST API-06 幂等说明）
            log.info("告警已是已处理状态，幂等返回成功：alarmId={}, handledBy={}", alarm.getAlarmId(), handledBy);
        }
    }

    /**
     * MQTT alarm 消息入库（MQTT v1.2 §7 → DB V0.5 §6.2）。
     * <p>去重契约（DB V0.5 起）：设备端无 RTC，{@code alarm_seq} 仅存 RAM，重启后从 0001 重新编号，
     * 因此 {@code alarm_id} 只在设备单次运行周期内唯一，不能作为全局幂等键。
     * 现行规则为「同一 alarm_id 在 10 秒窗口内判重」：窗口内重复投递（QoS1 重传）丢弃，
     * 窗口外同号消息（设备重启后重新计数）正常入库。
     * <p>并发窗口下若两路消息同时通过前置查询，insert 触发 {@code uk_alarm_id_created}
     * 唯一索引冲突，由 catch DuplicateKeyException 兜底忽略，两者语义一致。
     * <p>入库值：created_at / status / level 由实体默认值或数据库默认值生成，
     * created_at 即服务端接收时间（DB V0.3 §6.2）。
     *
     * @param alarm 从 MQTT alarm 主题消息解析出的告警实体
     */
    @Override
    public void saveFromMqtt(Alarm alarm) {
        // ① 先查后插去重：同一 alarm_id 在 10 秒窗口内已入库则丢弃本条消息（只告警一次）
        if (alarmMapper.selectRecentByAlarmId(alarm.getAlarmId(), ALARM_DEDUP_WINDOW_SECOND) != null) {
            log.info("重复 alarm_id（{}秒窗口内），丢弃：{}", ALARM_DEDUP_WINDOW_SECOND, alarm.getAlarmId());
            return;
        }

        // ② 插入告警：created_at/status/level 由实体或数据库默认值生成（服务端接收时间）
        try {
            alarmMapper.insert(alarm);
            log.info("告警入库成功：alarmId={}, type={}, classroomId={}",
                    alarm.getAlarmId(), alarm.getAlarmType(), alarm.getClassroomId());
        } catch (DuplicateKeyException e) {
            // ③ 兜底：并发窗口下 uk_alarm_id_created 冲突，与"先查后插"去重语义一致，忽略即可
            log.warn("并发插入触发 uk_alarm_id_created 唯一索引冲突，忽略：alarmId={}", alarm.getAlarmId());
        }
    }
}
