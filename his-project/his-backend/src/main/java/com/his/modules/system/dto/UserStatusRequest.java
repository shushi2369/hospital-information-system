package com.his.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserStatusRequest {
    @NotNull(message = "状态不能为空")
    private Integer status;
}
