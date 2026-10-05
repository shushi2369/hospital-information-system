package com.his.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserStatusRequest {
    /** 1 启用 0 停用——裸收任意值会造出"列表两视图都看不见、禁用踢线绕过"的幽灵账号 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态仅允许 0 停用 / 1 启用")
    @Max(value = 1, message = "状态仅允许 0 停用 / 1 启用")
    private Integer status;
}
