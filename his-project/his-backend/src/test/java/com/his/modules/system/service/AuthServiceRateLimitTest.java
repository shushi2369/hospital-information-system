package com.his.modules.system.service;

import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.security.JwtUtil;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.mapper.SysLoginLogMapper;
import com.his.modules.system.mapper.SysMenuMapper;
import com.his.modules.system.mapper.SysRoleMapper;
import com.his.modules.system.mapper.SysUserMapper;
import com.his.modules.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录限流（三十八轮修复的回归固化）：连续失败计数 >5 触发 A0007（正确密码也拒）、
 * 成功登录清零计数、Redis 不可用 fail-open。
 */
class AuthServiceRateLimitTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysLoginLogMapper loginLogMapper = mock(SysLoginLogMapper.class);
    private final com.his.infrastructure.security.SessionService sessionService =
            mock(com.his.infrastructure.security.SessionService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private AuthService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        // expire/delete 是 RedisTemplate 的 void/default 方法：mock 默认即无操作，
        // 显式 doNothing 会触发 default 方法自调用陷阱（RedisOperations.expire）
        service = new AuthService(userMapper, userRoleMapper, roleMapper, menuMapper,
                loginLogMapper, sessionService, jwtUtil, encoder, redis);
    }

    private SysUser activeUser(String rawPassword) {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("nurse.wang");
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setStatus(1);
        return u;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.1.2.3");
        return req;
    }

    @Test
    void consecutiveFails_triggerLimitEvenWithCorrectPassword() {
        when(userMapper.selectByUsername("nurse.wang")).thenReturn(activeUser("Right-1"));
        // 第 6 次尝试：计数 6 > 5 → 即使这次密码正确也必须拒（防爆破在成功边上停手）
        when(valueOps.increment(anyString())).thenReturn(6L);

        BizException e = assertThrows(BizException.class, () -> service.login(
                loginReq("Right-1"), request()));
        assertEquals(ErrorCode.A0007, e.getErrorCode());
        verify(userMapper, never()).selectByUsername(anyString()); // 限流先于查库
    }

    @Test
    void successfulLogin_clearsFailCounter() {
        when(userMapper.selectByUsername("nurse.wang")).thenReturn(activeUser("Right-1"));
        when(valueOps.increment(anyString())).thenReturn(1L); // 首次尝试
        when(roleMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of("NURSE"));

        var resp = service.login(loginReq("Right-1"), request());

        assertEquals(7L, resp.getUserId());
        // login 在密码校验后与 lastLogin 更新后各清零一次（实现细节），至少一次即达成清零语义
        verify(redis, org.mockito.Mockito.atLeastOnce()).delete(eq("login:fail:nurse.wang:10.1.2.3"));
    }

    @Test
    void redisOutage_failOpenInsteadOfLockout() {
        when(userMapper.selectByUsername("nurse.wang")).thenReturn(activeUser("Right-1"));
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        // Redis 挂掉：限流 fail-open（放行到密码校验），错误密码仍报 A0002 而非 C9001/A0007
        BizException e = assertThrows(BizException.class, () -> service.login(
                loginReq("wrong"), request()));
        assertEquals(ErrorCode.A0002, e.getErrorCode());
    }

    private com.his.modules.system.dto.LoginRequest loginReq(String password) {
        com.his.modules.system.dto.LoginRequest req = new com.his.modules.system.dto.LoginRequest();
        req.setUsername("nurse.wang");
        req.setPassword(password);
        return req;
    }
}
