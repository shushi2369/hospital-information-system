package com.his.modules.system.controller;

import com.his.common.AuditLog;
import com.his.common.R;
import com.his.infrastructure.security.LoginUser;
import com.his.modules.system.dto.LoginRequest;
import com.his.modules.system.dto.LoginResponse;
import com.his.modules.system.dto.MenuNode;
import com.his.modules.system.dto.PasswordChangeRequest;
import com.his.modules.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口（A-01~A-05）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return R.ok(authService.login(req, request));
    }

    @PostMapping("/logout")
    @AuditLog(module = "system", action = "登出")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @GetMapping("/me")
    public R<LoginUser> me() {
        return R.ok(authService.me());
    }

    @GetMapping("/menus")
    public R<List<MenuNode>> menus() {
        return R.ok(authService.menus());
    }

    @PutMapping("/password")
    @AuditLog(module = "system", action = "修改密码")
    public R<Void> changePassword(@Valid @RequestBody PasswordChangeRequest req) {
        authService.changePassword(req);
        return R.ok();
    }
}
