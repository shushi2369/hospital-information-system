package com.his.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserUpdateRequest {
    @NotBlank(message = "姓名不能为空")
    private String realName;
    private String phone;
    private Set<Long> roleIds;
}
