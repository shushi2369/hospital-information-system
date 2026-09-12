package com.his.modules.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应（不含 passwordHash）。
 */
@Getter
@Setter
public class UserResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleNames;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
