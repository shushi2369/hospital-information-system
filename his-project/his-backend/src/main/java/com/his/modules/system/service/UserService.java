package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.SessionService;
import com.his.modules.system.dto.UserCreateRequest;
import com.his.modules.system.dto.UserPageQuery;
import com.his.modules.system.dto.UserResponse;
import com.his.modules.system.dto.UserUpdateRequest;
import com.his.modules.system.entity.SysRole;
import com.his.modules.system.entity.SysUser;
import com.his.modules.system.entity.SysUserRole;
import com.his.modules.system.mapper.SysRoleMapper;
import com.his.modules.system.mapper.SysUserMapper;
import com.his.modules.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理（S-01~S-05）。
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserResponse> page(UserPageQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(query.getUsername() != null && !query.getUsername().isBlank(), SysUser::getUsername, LikeEscapeUtil.escape(query.getUsername()))
                .like(query.getRealName() != null && !query.getRealName().isBlank(), SysUser::getRealName, LikeEscapeUtil.escape(query.getRealName()))
                // 七十九轮：状态筛选（验收测试账号卫生）
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getId);
        if (query.getRoleId() != null) {
            List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, query.getRoleId()))
                    .stream().map(SysUserRole::getUserId).toList();
            if (userIds.isEmpty()) {
                PageResult<UserResponse> empty = new PageResult<>();
                empty.setTotal(0);
                empty.setList(List.of());
                return empty;
            }
            wrapper.in(SysUser::getId, userIds);
        }
        Page<SysUser> page = userMapper.selectPage(query.toPage(), wrapper);
        Map<Long, UserResponse> assembled = toResponses(page.getRecords());
        PageResult<UserResponse> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(page.getRecords().stream().map(u -> assembled.get(u.getId())).toList());
        return result;
    }

    /** 批量组装用户响应（一次查角色绑定 + 一次查角色名，避免分页 N+1） */
    private Map<Long, UserResponse> toResponses(List<SysUser> users) {
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        Map<Long, List<Long>> roleIdsByUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUserRole ur : userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds))) {
                roleIdsByUser.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur.getRoleId());
            }
        }
        List<Long> allRoleIds = roleIdsByUser.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, String> roleNames = allRoleIds.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(allRoleIds).stream()
                        .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        Map<Long, UserResponse> result = new LinkedHashMap<>();
        for (SysUser user : users) {
            UserResponse resp = new UserResponse();
            resp.setId(user.getId());
            resp.setUsername(user.getUsername());
            resp.setRealName(user.getRealName());
            resp.setPhone(user.getPhone());
            resp.setStatus(user.getStatus());
            resp.setLastLoginAt(user.getLastLoginAt());
            resp.setCreatedAt(user.getCreatedAt());
            List<Long> roleIds = roleIdsByUser.getOrDefault(user.getId(), List.of());
            resp.setRoleIds(roleIds);
            resp.setRoleNames(roleIds.stream().map(rid -> roleNames.getOrDefault(rid, String.valueOf(rid))).toList());
            result.put(user.getId(), resp);
        }
        return result;
    }

    @Transactional
    public Long create(UserCreateRequest req) {
        // 一百一十二轮 B06：与重置同口径（DTO 只有 @Size(min=8)，纯数字弱口令可建档）
        validatePasswordStrength(req.getPassword());
        // 与修改同口径：非管理员建档时不得直接绑定 ADMIN 角色（防委派用户管理后自建超管）
        if (req.getRoleIds().contains(1L)
                && !com.his.infrastructure.security.CurrentUser.get().getRoleCodes().contains("ADMIN")) {
            throw new BizException(ErrorCode.A0003, "仅管理员可分配管理员角色");
        }
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw new BizException(ErrorCode.B5003);
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.B5003);
        }
        bindRoles(user.getId(), req.getRoleIds());
        return user.getId();
    }

    @Transactional
    public void update(Long id, UserUpdateRequest req) {
        requireNotProtected(id, "修改内置超级管理员账号");
        SysUser user = requireUser(id);
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        if (userMapper.updateById(user) != 1) {
            throw new BizException(ErrorCode.A0008, "账号已被他人修改，请刷新后重试");
        }
        if (req.getRoleIds() != null) {
            // 一百一十二轮子代理 B01：非管理员不得授予/收回 ADMIN 角色（防自我提权）
            boolean grantAdmin = req.getRoleIds().contains(1L);
            if (grantAdmin && !com.his.infrastructure.security.CurrentUser.get().getRoleCodes().contains("ADMIN")) {
                throw new BizException(ErrorCode.A0003, "仅管理员可分配管理员角色");
            }
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            bindRoles(id, req.getRoleIds());
            sessionService.removeStrict(id);
        }
    }

    public void updateStatus(Long id, Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BizException(ErrorCode.A0001, "状态取值 0 停用 / 1 启用");
        }
        requireNotProtected(id, "内置超级管理员账号不可停用");
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == 0) {
            // 禁用即时踢下线（《04》§5）；一百一十四轮 S-04：强一致版，删除失败显式报错
            sessionService.removeStrict(id);
        }
    }

    public void resetPassword(Long id, String newPassword) {
        requireNotProtected(id, "内置超级管理员密码不可经管理端重置");
        validatePasswordStrength(newPassword);
        SysUser user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        sessionService.removeStrict(id);
    }

    /** 内置超管保护（一百一十二轮子代理 B01 P0）：id=1 / username=admin 的账号
     *  禁止经管理端修改/停用/重置密码，防锁死真超管后被接管 */
    private void requireNotProtected(Long id, String message) {
        SysUser user = userMapper.selectById(id);
        if (user != null && (user.getId() == 1L || "admin".equals(user.getUsername()))) {
            throw new BizException(ErrorCode.A0003, message);
        }
    }

    /** 密码强度（等保基线）：≥8 位且同时含字母与数字 */
    public static void validatePasswordStrength(String pwd) {
        if (pwd == null || pwd.length() < 8 || !pwd.matches(".*[A-Za-z].*") || !pwd.matches(".*[0-9].*")) {
            throw new BizException(ErrorCode.A0001, "密码须至少 8 位且同时包含字母与数字");
        }
    }

    private void bindRoles(Long userId, Set<Long> roleIds) {
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.A0001, "用户不存在");
        }
        return user;
    }
}
