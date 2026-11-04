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
import com.his.modules.system.mapper.SysUserMapper;
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
    private final SysUserMapper userMapper;

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
        try {
            roleMapper.insert(role);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.B5001);
        }
        return role.getId();
    }

    public List<Long> getMenuIds(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Transactional
    public void updateMenus(Long roleId, Set<Long> menuIds) {
        // 一百一十二轮子代理 B01 P0：内置 ADMIN 角色权限集禁改（改写即全局提权/降权）
        if (roleId == 1L) {
            throw new BizException(ErrorCode.A0003, "内置管理员角色权限集不可修改");
        }
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).distinct().sorted().toList();
        // 与登录保持「用户 → 角色菜单」顺序；多角色共享用户时按 ID 排序，避免交叉加锁。
        // 必须先等待已读取旧权限的登录保存会话，再改权限并撤销，防止旧权限会话写回。
        userIds.forEach(userMapper::lockByIdForUpdate);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
        // 五十七轮：权限变更即时生效——失效持有该角色的全部在线会话。
        // 会话缓存 roleCodes/permissions 且滑动续期，不失效则权限回收在活跃用户身上可能无限期悬空。
        // 撤销失败回滚权限变更，不能将未生效的权限回收报告为成功。
        userIds.forEach(sessionService::removeStrict);
    }
}
