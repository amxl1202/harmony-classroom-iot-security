package com.example.classguard.mapper;

import com.example.classguard.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备表数据访问（devices，见《数据库设计说明书（第8组）》V0.2 §4.4/§6.3）。
 * <p>SQL 统一写在 resources/mappers/DeviceMapper.xml。
 */
@Mapper
public interface DeviceMapper {

    /** 设备列表与在线状态（API-08，管理员） */
    List<Device> selectAll();

    /** 按联合主键查询（API-09 下发指令前定位教室编号 classroom_id） */
    Device selectByPk(@Param("classroomId") String classroomId, @Param("deviceId") String deviceId);

    /** 在线状态更新（MQTT status/LWT，UPSERT 语义）：
     * INSERT ... ON DUPLICATE KEY UPDATE online=VALUES(online), last_online_at=VALUES(last_online_at)，
     * 设备首次上线自动建档 */
    int upsert(Device device);
}
