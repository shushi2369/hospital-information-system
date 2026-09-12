package com.his.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.system.entity.SysRole;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(Long userId);
}
