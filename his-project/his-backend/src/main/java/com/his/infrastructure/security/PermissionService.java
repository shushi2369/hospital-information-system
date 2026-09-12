package com.his.infrastructure.security;

import org.springframework.stereotype.Component;

/**
 * @PreAuthorize("@ss.hasPerm('xxx')") 的权限码判定入口（SpEL bean 名 "ss"）。
 */
@Component("ss")
public class PermissionService {

    public boolean hasPerm(String perm) {
        try {
            LoginUser user = CurrentUser.get();
            return user.getPermissions() != null && user.getPermissions().contains(perm);
        } catch (Exception e) {
            return false;
        }
    }
}
