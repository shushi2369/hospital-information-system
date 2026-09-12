package com.his.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.system.entity.SysMenu;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("<script>SELECT DISTINCT m.permission_code FROM sys_menu m "
            + "JOIN sys_role_menu rm ON m.id = rm.menu_id "
            + "WHERE m.status = 1 AND m.menu_type = 3 AND m.permission_code IS NOT NULL "
            + "AND rm.role_id IN "
            + "<foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach>"
            + "</script>")
    List<String> selectPermCodesByRoleIds(List<Long> roleIds);

    @Select("<script>SELECT DISTINCT m.* FROM sys_menu m "
            + "JOIN sys_role_menu rm ON m.id = rm.menu_id "
            + "WHERE m.status = 1 AND m.menu_type IN (1, 2) "
            + "AND rm.role_id IN "
            + "<foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach> "
            + "ORDER BY m.sort_no</script>")
    List<SysMenu> selectMenusByRoleIds(List<Long> roleIds);
}
