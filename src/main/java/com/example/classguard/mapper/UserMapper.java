package com.example.classguard.mapper;

import com.example.classguard.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户表数据访问（users，见《数据库设计说明书（第8组）》V0.2 §4.1/§6.3）。
 * <p>SQL 统一写在 resources/mappers/UserMapper.xml，使用 #{} 预编译占位符，禁止 ${}。
 */
@Mapper
public interface UserMapper {

    /** 登录查询（API-02）：SELECT id, username, password, role FROM users WHERE username = #{username} */
    User selectByUsername(@Param("username") String username);

    /** 当前用户信息（API-04）：按主键查询 */
    User selectById(@Param("id") Long id);

    /** 注册（role=user，used_invite_code 记录所用邀请码，REST v1.2 API-01 / DB V0.4 §6.3(2)）与预置管理员（role=admin，used_invite_code 为 NULL）共用：显式插入 username/password/role/used_invite_code 四字段并回填自增 id */
    int insert(User user);

    /** 修改密码（API-03）：UPDATE password（入参为 BCrypt 哈希后的值） */
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
