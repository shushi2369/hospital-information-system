package com.his.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleCreateRequest {
    @NotBlank(message = "角色编码不能为空")
    /** 一百一十二轮 B15a：与前端 2-32 位字母数字下划线口径对齐（原后端可建任意畸形编码） */
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_]{2,32}", message = "角色编码须为 2~32 位字母/数字/下划线")
    private String roleCode;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    private String description;
}
