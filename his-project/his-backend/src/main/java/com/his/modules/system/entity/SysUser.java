package com.his.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户（passwordHash 仅 BCrypt 散列存储，任何响应不得外传）。
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String passwordHash;
    private String realName;
    private String phone;
    private LocalDateTime lastLoginAt;
    private Integer status;

    @Version
    private Integer version;
}
