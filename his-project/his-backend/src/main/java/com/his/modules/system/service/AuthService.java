package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.security.JwtUtil;
import com.his.infrastructure.security.LoginUser;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.dto.LoginRequest;
import com.his.modules.system.dto.LoginResponse;
import com.his.modules.system.dto.MenuNode;
import com.his.modules.system.dto.PasswordChangeRequest;
import com.his.modules.system.entity.SysLoginLog;
import com.his.modules.system.entity.SysMenu;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.entity.SysUserRole;
import com.his.modules.system.mapper.SysLoginLogMapper;
import com.his.modules.system.mapper.SysMenuMapper;
import com.his.modules.system.mapper.SysRoleMapper;
import com.his.modules.system.mapper.SysUserMapper;
import com.his.modules.system.mapper.SysUserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证服务（《04》§5）：限流 → BCrypt 校验 → 组装权限 → Redis 会话 → 签发 JWT，全程留痕。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int LOGIN_FAIL_LIMIT = 5;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SessionService sessionService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    public LoginResponse login(LoginRequest req, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String ua = request.getHeader("User-Agent");
        String failKey = "login:fail:" + req.getUsername() + ":" + ip;

        Long fails = null;
        try {
            fails = redis.opsForValue().increment(failKey);
            if (fails != null && fails == 1) {
                redis.expire(failKey, Duration.ofSeconds(60));
            }
        } catch (Exception e) {
            log.warn("登录限流计数不可用: {}", e.getMessage());
        }
        if (fails != null && fails > LOGIN_FAIL_LIMIT) {
            saveLoginLog(req.getUsername(), ip, ua, false, "触发登录限流");
            throw new BizException(ErrorCode.A0007);
        }

        SysUser user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            saveLoginLog(req.getUsername(), ip, ua, false, "用户名或密码错误");
            throw new BizException(ErrorCode.A0002, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            saveLoginLog(req.getUsername(), ip, ua, false, "账号已被禁用");
            throw new BizException(ErrorCode.A0002, "账号已被禁用");
        }

        Set<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).collect(java.util.stream.Collectors.toSet());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setRoleIds(roleIds);
        loginUser.setRoleCodes(new HashSet<>(roleMapper.selectRoleCodesByUserId(user.getId())));
        loginUser.setPermissions(roleIds.isEmpty() ? Set.of() : new HashSet<>(menuMapper.selectPermCodesByRoleIds(List.copyOf(roleIds))));
        sessionService.save(loginUser);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        try {
            redis.delete(failKey);
        } catch (Exception ignore) {
            // 限流计数清理失败不影响登录
        }
        saveLoginLog(user.getUsername(), ip, ua, true, "登录成功");

        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtUtil.generate(user.getId(), user.getUsername()));
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoleCodes(loginUser.getRoleCodes());
        resp.setPermissions(loginUser.getPermissions());
        return resp;
    }

    public void logout() {
        sessionService.remove(CurrentUser.id());
    }

    public LoginUser me() {
        return CurrentUser.get();
    }

    public List<MenuNode> menus() {
        Set<Long> roleIds = CurrentUser.get().getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return MenuService.buildTree(menuMapper.selectMenusByRoleIds(List.copyOf(roleIds)));
    }

    public void changePassword(PasswordChangeRequest req) {
        SysUser user = userMapper.selectById(CurrentUser.id());
        if (user == null) {
            throw new BizException(ErrorCode.A0002);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.A0001, "旧密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(update);
        // 修改密码后强制重新登录
        sessionService.remove(user.getId());
    }

    private void saveLoginLog(String username, String ip, String ua, boolean success, String message) {
        try {
            SysLoginLog logEntity = new SysLoginLog();
            logEntity.setUsername(username);
            logEntity.setIp(ip);
            logEntity.setUserAgent(ua != null && ua.length() > 256 ? ua.substring(0, 256) : ua);
            logEntity.setSuccess(success ? 1 : 0);
            logEntity.setMessage(message);
            loginLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.error("登录日志写入失败", e);
        }
    }
}
