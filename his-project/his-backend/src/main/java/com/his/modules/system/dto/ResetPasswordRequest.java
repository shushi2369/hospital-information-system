package com.his.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度须在 8~32 位之间")
    private String newPassword;
}
