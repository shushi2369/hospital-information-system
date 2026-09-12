package com.his.modules.system.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.modules.system.dto.RoleCreateRequest;
import com.his.modules.system.dto.RoleMenuUpdateRequest;
import com.his.modules.system.entity.SysRole;
import com.his.modules.system.service.RoleService;
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

import java.util.List;

/**
 * 角色管理接口（S-06~S-08）。
 */
@RestController
@RequestMapping("/api/v1/system/roles")
@RequiredArgsConstructor
public class SysRoleController {
    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("@ss.hasPerm('sys:role:query')")
    public R<List<SysRole>> listAll() {
        return R.ok(roleService.listAll());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('sys:role:manage')")
    @Idempotent
    @AuditLog(module = "system", action = "新增角色", bizType = "sys_role")
    public R<Long> create(@Valid @RequestBody RoleCreateRequest req) {
        return R.ok(roleService.create(req));
    }

    @GetMapping("/{id}/menus")
    @PreAuthorize("@ss.hasPerm('sys:role:query')")
    public R<List<Long>> getMenuIds(@PathVariable Long id) {
        return R.ok(roleService.getMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("@ss.hasPerm('sys:role:manage')")
    @Idempotent
    @AuditLog(module = "system", action = "配置角色权限", bizType = "sys_role")
    public R<Void> updateMenus(@PathVariable Long id, @Valid @RequestBody RoleMenuUpdateRequest req) {
        roleService.updateMenus(id, req.getMenuIds());
        return R.ok();
    }
}
