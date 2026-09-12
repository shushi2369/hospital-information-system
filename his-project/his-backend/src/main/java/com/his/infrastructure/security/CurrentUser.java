package com.his.infrastructure.security;

import com.his.common.BizException;
import com.his.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户读取入口。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static LoginUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        throw new BizException(ErrorCode.A0002);
    }

    public static Long id() {
        return get().getUserId();
    }

    public static boolean authenticated() {
        try {
            get();
            return true;
        } catch (BizException e) {
            return false;
        }
    }
}
