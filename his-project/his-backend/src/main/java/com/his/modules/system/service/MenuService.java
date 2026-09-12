package com.his.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.system.dto.MenuNode;
import com.his.modules.system.entity.SysMenu;
import com.his.modules.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单与权限点查询。
 */
@Service
@RequiredArgsConstructor
public class MenuService {
    private final SysMenuMapper menuMapper;

    /** 全量菜单树（角色配置用） */
    public List<MenuNode> tree() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getMenuType, 1, 2)
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSortNo));
        return buildTree(menus);
    }

    /** 按角色集合取授权菜单树 */
    public List<MenuNode> treeByRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return buildTree(menuMapper.selectMenusByRoleIds(List.copyOf(roleIds)));
    }

    /** 按角色集合取权限码 */
    public Set<String> listPermsByRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(menuMapper.selectPermCodesByRoleIds(List.copyOf(roleIds)));
    }

    public static List<MenuNode> buildTree(List<SysMenu> menus) {
        List<MenuNode> nodes = menus.stream()
                .sorted(Comparator.comparing(SysMenu::getSortNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(MenuNode::of)
                .collect(Collectors.toList());
        Map<Long, MenuNode> byId = nodes.stream().collect(Collectors.toMap(MenuNode::getId, n -> n));
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes) {
            MenuNode parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }
}
