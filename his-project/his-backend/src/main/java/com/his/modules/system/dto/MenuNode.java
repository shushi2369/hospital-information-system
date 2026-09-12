package com.his.modules.system.dto;

import com.his.modules.system.entity.SysMenu;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（前端动态路由与角色配置树共用）。
 */
@Getter
@Setter
public class MenuNode {
    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String permissionCode;
    private String path;
    private String component;
    private Integer sortNo;
    private List<MenuNode> children = new ArrayList<>();

    public static MenuNode of(SysMenu menu) {
        MenuNode node = new MenuNode();
        node.setId(menu.getId());
        node.setParentId(menu.getParentId());
        node.setMenuName(menu.getMenuName());
        node.setMenuType(menu.getMenuType());
        node.setPermissionCode(menu.getPermissionCode());
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());
        node.setSortNo(menu.getSortNo());
        return node;
    }
}
