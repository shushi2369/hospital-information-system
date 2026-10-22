package com.his.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.system.entity.SysUser;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(String username);

    /** 护士排班下拉：只取启用状态的 NURSE 角色账号（原页面用 /system/users，护士无该权限且混入全部用户） */
    @Select("SELECT DISTINCT u.id, u.username, u.real_name, u.status FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id "
            + "JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE r.role_code = 'NURSE' AND r.status = 1 AND u.status = 1 ORDER BY u.id")
    List<SysUser> selectNurseUsers();

    @Select("SELECT COUNT(*) FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id "
            + "JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE u.id = #{userId} AND r.role_code = 'NURSE' AND r.status = 1 AND u.status = 1")
    int countNurseRole(Long userId);
}
