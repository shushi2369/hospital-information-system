package com.his.modules.system.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.entity.SysRole;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.mapper.SysRoleMapper;
import com.his.modules.system.mapper.SysUserMapper;
import com.his.modules.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** UserService 单测：内置超管三层防线（112 轮 B01 P0）+ 密码强度 + 建模用户名唯一。 */
class UserServiceTest extends UnitTestBase {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder =
            mock(org.springframework.security.crypto.password.PasswordEncoder.class);

    private UserService svc() {
        return new UserService(userMapper, userRoleMapper, roleMapper, sessionService, encoder);
    }

    private SysUser user(Long id, String name) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(name);
        return u;
    }

    // ---------------- 内置超管保护 ----------------

    @Test
    void superadmin_cannotBeDisabled() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin"));
        BizException ex = assertThrows(BizException.class,
                () -> svc().updateStatus(1L, 0));
        assertEquals(com.his.common.ErrorCode.A0003, ex.getErrorCode());
    }

    @Test
    void superadmin_passwordCannotBeResetViaAdmin() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin"));
        BizException ex = assertThrows(BizException.class,
                () -> svc().resetPassword(1L, "NewPass123"));
        assertEquals(com.his.common.ErrorCode.A0003, ex.getErrorCode());
    }

    @Test
    void superadmin_profileCannotBeModified() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin"));
        com.his.modules.system.dto.UserUpdateRequest req =
                new com.his.modules.system.dto.UserUpdateRequest();
        BizException ex = assertThrows(BizException.class,
                () -> svc().update(1L, req));
        assertEquals(com.his.common.ErrorCode.A0003, ex.getErrorCode());
    }

    @Test
    void nonAdmin_cannotGrantAdminRole() {
        setupAs(9L, "DOCTOR"); // 非管理员操作者
        SysUser target = user(9L, "dr.li");
        when(userMapper.selectById(9L)).thenReturn(target);
        when(userMapper.updateById(any())).thenReturn(1);
        com.his.modules.system.dto.UserUpdateRequest req =
                new com.his.modules.system.dto.UserUpdateRequest();
        req.setRoleIds(java.util.Set.of(1L, 2L)); // 含 ADMIN(1)
        BizException ex = assertThrows(BizException.class,
                () -> svc().update(9L, req));
        assertEquals(com.his.common.ErrorCode.A0003, ex.getErrorCode());
    }

    // ---------------- updateStatus 白名单 ----------------

    @Test
    void updateStatus_rejectsInvalidValue() {
        SysUser target = user(9L, "dr.li");
        when(userMapper.selectById(9L)).thenReturn(target);
        BizException ex = assertThrows(BizException.class,
                () -> svc().updateStatus(9L, 3));
        assertEquals(com.his.common.ErrorCode.A0001, ex.getErrorCode());
    }

    @Test
    void disable_kicksSessionImmediately() {
        SysUser target = user(9L, "dr.li");
        when(userMapper.selectById(9L)).thenReturn(target);
        when(userMapper.updateById(any())).thenReturn(1);
        svc().updateStatus(9L, 0);
        verify(sessionService).removeStrict(9L);
    }

    // ---------------- 密码强度 ----------------

    @Test
    void password_tooShortRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> svc().resetPassword(9L, "Ab1"));
        assertEquals(com.his.common.ErrorCode.A0001, ex.getErrorCode());
    }

    @Test
    void password_pureDigitsRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> svc().resetPassword(9L, "12345678"));
        assertEquals(com.his.common.ErrorCode.A0001, ex.getErrorCode());
    }

    @Test
    void password_validAccepted() {
        SysUser target = user(9L, "dr.li");
        when(userMapper.selectById(9L)).thenReturn(target);
        when(userMapper.updateById(any())).thenReturn(1);
        when(encoder.encode("GoodPass123")).thenReturn("hash");
        assertDoesNotThrow(() -> svc().resetPassword(9L, "GoodPass123"));
    }

    // ---------------- 建模用户名唯一 ----------------

    @Test
    void create_duplicateUsernameRejected() {
        when(userMapper.selectByUsername("admin")).thenReturn(user(1L, "admin"));
        com.his.modules.system.dto.UserCreateRequest req =
                new com.his.modules.system.dto.UserCreateRequest();
        req.setUsername("admin");
        req.setPassword("GoodPass123");
        req.setRoleIds(java.util.Set.of(2L));
        BizException ex = assertThrows(BizException.class, () -> svc().create(req));
        assertEquals(com.his.common.ErrorCode.B5003, ex.getErrorCode());
    }
}
