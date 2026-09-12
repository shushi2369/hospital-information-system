package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
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
                .like(query.getUsername() != null && !query.getUsername().isBlank(), SysUser::getUsername, query.getUsername())
                .like(query.getRealName() != null && !query.getRealName().isBlank(), SysUser::getRealName, query.getRealName())
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
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw new BizException(ErrorCode.B5003);
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setStatus(1);
        userMapper.insert(user);
        bindRoles(user.getId(), req.getRoleIds());
        return user.getId();
    }

    @Transactional
    public void update(Long id, UserUpdateRequest req) {
        SysUser user = requireUser(id);
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        userMapper.updateById(user);
        if (req.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            bindRoles(id, req.getRoleIds());
            sessionService.remove(id);
        }
    }

    public void updateStatus(Long id, Integer status) {
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == 0) {
            // 禁用即时踢下线（《04》§5）
            sessionService.remove(id);
        }
    }

    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        sessionService.remove(id);
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
