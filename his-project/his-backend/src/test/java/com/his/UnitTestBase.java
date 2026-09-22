package com.his;

import com.his.infrastructure.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * 单测基类：设置 SecurityContext 供 CurrentUser.id() 等静态方法使用。
 * 子类在 @BeforeEach 中调用 setupAs(userId, roles...) 设置当前用户。
 */
public abstract class UnitTestBase {
    @BeforeEach
    void baseSetUp() {
        setupAs(1L, "ADMIN");
    }

    @AfterEach
    void baseTearDown() {
        SecurityContextHolder.clearContext();
    }

    protected void setupAs(Long userId, String... roles) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("test" + userId);
        user.setRealName("测试用户" + userId);
        user.setRoleIds(Set.of(roles.length > 0 ? 1L : 0L));
        user.setRoleCodes(Set.of(roles));
        user.setPermissions(Set.of());
        var auth = new UsernamePasswordAuthenticationToken(user, null, java.util.Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
