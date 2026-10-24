package com.his.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    /** 一百二十四轮【01】P2：@Size 收口——超长 username 会让登录审计落库抛 Data too long
     *  被吞掉（失败登录/限流事件零留痕），且 failKey 长度无界直接进 Redis 键空间 */
    @NotBlank(message = "用户名不能为空")
    @jakarta.validation.constraints.Size(max = 32, message = "用户名最长 32 位")
    private String username;
    @NotBlank(message = "密码不能为空")
    @jakarta.validation.constraints.Size(max = 64, message = "密码最长 64 位")
    private String password;
}
