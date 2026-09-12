package com.his.modules.system.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.system.dto.ResetPasswordRequest;
import com.his.modules.system.dto.UserCreateRequest;
import com.his.modules.system.dto.UserPageQuery;
import com.his.modules.system.dto.UserResponse;
import com.his.modules.system.dto.UserStatusRequest;
import com.his.modules.system.dto.UserUpdateRequest;
import com.his.modules.system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（S-01~S-05）。
 */
@RestController
@RequestMapping("/api/v1/system/users")
@RequiredArgsConstructor
public class SysUserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:user:query')")
    public R<PageResult<UserResponse>> page(UserPageQuery query) {
        return R.ok(userService.page(query));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:user:create')")
    @Idempotent
    @AuditLog(module = "system", action = "新增用户", bizType = "sys_user")
    public R<Long> create(@Valid @RequestBody UserCreateRequest req) {
        return R.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('sys:user:update')")
    @Idempotent
    @AuditLog(module = "system", action = "修改用户", bizType = "sys_user")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        userService.update(id, req);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPerm('sys:user:update')")
    @Idempotent
    @AuditLog(module = "system", action = "启用禁用用户", bizType = "sys_user")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest req) {
        userService.updateStatus(id, req.getStatus());
        return R.ok();
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("@ss.hasPerm('sys:user:manage')")
    @Idempotent
    @AuditLog(module = "system", action = "重置密码", bizType = "sys_user")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.getNewPassword());
        return R.ok();
    }
}
