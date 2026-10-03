package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.dto.RoleCreateRequest;
import com.his.modules.system.entity.SysRole;
import com.his.modules.system.entity.SysRoleMenu;
import com.his.modules.system.entity.SysUserRole;
import com.his.modules.system.mapper.SysRoleMapper;
import com.his.modules.system.mapper.SysRoleMenuMapper;
import com.his.modules.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 角色管理（S-06~S-08）。内置五角色仅允许调整权限集，不允许删除。
 */
@Service
@RequiredArgsConstructor
public class RoleService {
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SessionService sessionService;

    public List<SysRole> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
    }

    public Long create(RoleCreateRequest req) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, req.getRoleCode()));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.B5001, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(req.getRoleCode());
        role.setRoleName(req.getRoleName());
        role.setDescription(req.getDescription());
        role.setStatus(1);
        roleMapper.insert(role);
        return role.getId();
    }

    public List<Long> getMenuIds(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Transactional
    public void updateMenus(Long roleId, Set<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
        // 五十七轮：权限变更即时生效——失效持有该角色的全部在线会话。
        // 会话缓存 roleCodes/permissions 且滑动续期，不失效则权限回收在活跃用户身上可能无限期悬空。
        userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).distinct()
                .forEach(sessionService::remove);
    }
}
