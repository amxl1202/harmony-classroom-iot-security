package com.example.classguard.mapper;

import com.example.classguard.entity.Alarm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警表数据访问（alarms，见《数据库设计说明书（第8组）》V0.2 §4.2/§6.3）。
 * <p>SQL 统一写在 resources/mappers/AlarmMapper.xml：from/to 为闭区间可只传其一（&lt;if&gt; 动态拼接），
 * 排序统一 ORDER BY created_at DESC, id DESC。
 */
@Mapper
public interface AlarmMapper {

    /** 告警分页查询（API-05）：LIMIT #{offset}, #{limit} */
    List<Alarm> selectPage(@Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("status") Integer status,
                           @Param("offset") long offset,
                           @Param("limit") int limit);

    /** 告警分页总数（API-05），条件与 selectPage 一致 */
    long selectCount(@Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to,
                     @Param("status") Integer status);

    /** 按主键查询（API-06 前置校验，3001 判定） */
    Alarm selectById(@Param("id") Long id);

    /** 按 alarm_id 查询（MQTT alarm 消息去重：已存在则丢弃） */
    Alarm selectByAlarmId(@Param("alarmId") String alarmId);

    /** 告警插入（MQTT alarm 消息入库，created_at 默认 CURRENT_TIMESTAMP） */
    int insert(Alarm alarm);

    /** 标记已处理（API-06）：UPDATE status=1, handled_by=#{handledBy}, handled_at=NOW() WHERE id=#{id} AND status=0 */
    int markHandled(@Param("id") Long id, @Param("handledBy") String handledBy);
}
