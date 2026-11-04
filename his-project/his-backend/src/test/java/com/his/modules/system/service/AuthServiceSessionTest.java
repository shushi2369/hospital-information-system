package com.his.modules.system.service;

import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.security.JwtUtil;
import com.his.infrastructure.security.LoginUser;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.dto.PasswordChangeRequest;
import com.his.modules.system.entity.SysLoginLog;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 退出和改密只有在撤销会话成功后才能返回成功。 */
class AuthServiceSessionTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SessionService sessions = mock(SessionService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SysLoginLogMapper logs = mock(SysLoginLogMapper.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, mock(SysUserRoleMapper.class), mock(SysRoleMapper.class),
                mock(SysMenuMapper.class), logs, sessions, mock(JwtUtil.class), encoder,
                mock(StringRedisTemplate.class));
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutReportsSessionRevocationFailure() {
        doThrow(new BizException(ErrorCode.C9001)).when(sessions).removeStrict(7L);
        assertThrows(BizException.class, service::logout);
    }

    @Test
    void changePasswordReportsRevocationFailureForTransactionRollback() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setPasswordHash("old-hash");
        when(users.selectByIdForUpdate(7L)).thenReturn(user);
        when(encoder.matches("Old-pass1", "old-hash")).thenReturn(true);
        when(encoder.encode("New-pass2")).thenReturn("new-hash");
        doThrow(new BizException(ErrorCode.C9001)).when(sessions).removeStrict(7L);
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("Old-pass1");
        request.setNewPassword("New-pass2");

        assertThrows(BizException.class, () -> service.changePassword(request));
        var order = inOrder(users, sessions);
        order.verify(users).selectByIdForUpdate(7L);
        order.verify(users).updateById(any(SysUser.class));
        order.verify(sessions).removeStrict(7L);
    }

    @Test
    void loginLogMessageIsTruncatedBeforeInsert() {
        ReflectionTestUtils.invokeMethod(service, "saveLoginLog", "测试用户", "127.0.0.1", "测试客户端",
                false, "长".repeat(200));
        ArgumentCaptor<SysLoginLog> log = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(logs).insert(log.capture());
        assertEquals("长".repeat(128), log.getValue().getMessage());
    }
}
