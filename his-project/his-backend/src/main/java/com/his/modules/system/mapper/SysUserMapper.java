package com.his.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.system.entity.SysUser;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(String username);
}
