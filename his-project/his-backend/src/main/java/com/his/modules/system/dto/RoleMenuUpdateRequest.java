package com.his.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class RoleMenuUpdateRequest {
    @NotNull(message = "菜单权限集合不能为空")
    private Set<Long> menuIds;
}
