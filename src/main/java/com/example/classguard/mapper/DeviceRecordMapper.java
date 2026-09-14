package com.example.classguard.mapper;

import com.example.classguard.entity.DeviceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备执行记录表数据访问（device_records，见《数据库设计说明书（第8组）》V0.2 §4.3/§6.3）。
 * <p>SQL 统一写在 resources/mappers/DeviceRecordMapper.xml；排序 ORDER BY created_at DESC, id DESC。
 */
@Mapper
public interface DeviceRecordMapper {

    /** 执行历史分页查询（API-07，管理员）：from/to 闭区间可只传其一 */
    List<DeviceRecord> selectPage(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    /** 执行历史分页总数（API-07），条件与 selectPage 一致 */
    long selectCount(@Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);

    /** 回执幂等写入（MQTT cmd_resp）：INSERT IGNORE，依赖 uk_cmd_id 唯一索引，同一 cmd_id 仅首次生效 */
    int insertIgnore(DeviceRecord record);
}
