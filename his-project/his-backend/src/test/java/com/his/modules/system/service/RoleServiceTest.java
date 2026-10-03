package com.his.modules.system.service;

import com.his.UnitTestBase;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.entity.SysUserRole;
import com.his.modules.system.mapper.SysRoleMapper;
import com.his.modules.system.mapper.SysRoleMenuMapper;
import com.his.modules.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 五十七轮：角色权限变更即时失效持角用户会话（防权限回收在活跃用户上悬空）。 */
class RoleServiceTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SessionService sessionService = mock(SessionService.class);

    private final RoleService service = new RoleService(
            roleMapper, roleMenuMapper, userRoleMapper, sessionService);

    @Test
    void updateMenus_invalidatesSessionsOfAllRoleHolders() {
        SysUserRole ur1 = new SysUserRole();
        ur1.setUserId(11L);
        ur1.setRoleId(3L);
        SysUserRole ur2 = new SysUserRole();
        ur2.setUserId(12L);
        ur2.setRoleId(3L);
        SysUserRole dup = new SysUserRole();
        dup.setUserId(11L);   // 重复绑定行：会话只删一次
        dup.setRoleId(3L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(ur1, ur2, dup));

        service.updateMenus(3L, Set.of(101L, 102L));

        ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
        verify(sessionService, times(2)).remove(ids.capture());
        assertEquals(2, ids.getAllValues().size());
        assertTrue(ids.getAllValues().containsAll(List.of(11L, 12L)));
    }
}
