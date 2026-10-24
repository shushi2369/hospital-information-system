package com.his.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserUpdateRequest {
    @jakarta.validation.constraints.Size(max = 32, message = "realName最长 32 字")
    @NotBlank(message = "姓名不能为空")
    private String realName;
    @jakarta.validation.constraints.Size(max = 20, message = "phone最长 20 字")
    private String phone;
    private Set<Long> roleIds;
}
