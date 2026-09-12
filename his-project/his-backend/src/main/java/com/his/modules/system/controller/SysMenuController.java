package com.his.modules.system.controller;

import com.his.common.R;
import com.his.modules.system.dto.MenuNode;
import com.his.modules.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理接口（S-09，全量菜单树供角色配置）。
 */
@RestController
@RequestMapping("/api/v1/system/menus")
@RequiredArgsConstructor
public class SysMenuController {
    private final MenuService menuService;

    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPerm('sys:role:query')")
    public R<List<MenuNode>> tree() {
        return R.ok(menuService.tree());
    }
}
