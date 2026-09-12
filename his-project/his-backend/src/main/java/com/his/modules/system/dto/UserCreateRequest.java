package com.his.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名最长 32 位")
    @Pattern(regexp = "[a-zA-Z0-9_.]+", message = "用户名只允许字母、数字、点与下划线")
    private String username;
    @NotBlank(message = "姓名不能为空")
    private String realName;
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度须在 8~32 位之间")
    private String password;
    private String phone;
    @NotEmpty(message = "至少绑定一个角色")
    private Set<Long> roleIds;
}
