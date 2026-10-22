package com.example.classguard.mapper;

import com.example.classguard.entity.InviteCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 邀请码表数据访问（invite_codes，见《数据库设计说明书（第8组）》V0.4 §4.5/§6.3(9)~(12)）。
 * <p>SQL 统一写在 resources/mappers/InviteCodeMapper.xml，使用 #{} 预编译占位符，动态条件用 &lt;if&gt;，禁止 ${}；
 * 排序统一 ORDER BY created_at DESC, id DESC，分页统一 LIMIT #{offset}, #{limit}。
 */
@Mapper
public interface InviteCodeMapper {

    /** 按邀请码查询（API-01 注册校验，DB V0.4 §6.3(9)）：SQL 只按 code 查，"存在 + 未作废 + 未过期"三条件由应用层判定 */
    InviteCode selectByCode(@Param("code") String code);

    /** 按主键查询（API-12 前置校验，DB V0.4 §6.3(11)）：区分 3003 邀请码不存在与幂等 */
    InviteCode selectById(@Param("id") Long id);

    /** 邀请码生成（API-10，DB V0.4 §6.3(10)）：显式插入 code/status/expires_at/created_by 四字段，status 由调用方传 0，useGeneratedKeys 回填自增 id */
    int insert(InviteCode inviteCode);

    /** 邀请码分页查询（API-11 列表，DB V0.4 §6.3(12)）：ORDER BY created_at DESC, id DESC，LIMIT #{offset}, #{limit} */
    List<InviteCode> selectPage(@Param("offset") long offset, @Param("limit") int limit);

    /** 邀请码分页总数（API-11，DB V0.4 §6.3(12)） */
    long selectCount();

    /** 作废邀请码（API-12，DB V0.4 §6.3(11)）：UPDATE ... SET status=1 WHERE id=#{id} AND status=0，WHERE status=0 保证幂等 */
    int disable(@Param("id") Long id);
}
