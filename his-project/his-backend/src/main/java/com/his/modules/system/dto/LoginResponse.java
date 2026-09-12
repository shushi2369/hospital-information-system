package com.his.modules.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private Set<String> roleCodes;
    private Set<String> permissions;
}
