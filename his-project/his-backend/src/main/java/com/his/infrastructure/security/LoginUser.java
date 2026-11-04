package com.his.infrastructure.security;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户上下文（存入 Redis 会话与 SecurityContext）。
 */
@Getter
@Setter
public class LoginUser implements Serializable {
    private Long userId;
    /** 同一轮账户会话的随机标识，撤销或过期后重新生成。 */
    private String sessionId;
    private String username;
    private String realName;
    private Set<Long> roleIds;
    private Set<String> roleCodes;
    private Set<String> permissions;
}
