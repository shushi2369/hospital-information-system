package com.his.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单与权限点：menuType 1 目录 / 2 页面 / 3 按钮与接口权限点。
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String permissionCode;
    private String path;
    private String component;
    private Integer sortNo;
    private Integer status;

    @Version
    private Integer version;
}
