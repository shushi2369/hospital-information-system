package com.his.modules.registration.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.registration.dto.RegistrationCreateRequest;
import com.his.modules.registration.dto.RegistrationCreateResult;
import com.his.modules.registration.dto.RegistrationQuery;
import com.his.modules.registration.dto.RegistrationResponse;
import com.his.modules.registration.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 挂号接口（R-01~R-05）。
 */
@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping
    @PreAuthorize("@ss.hasPerm('reg:ticket:create')")
    @Idempotent
    @AuditLog(module = "registration", action = "挂号", bizType = "reg_registration")
    public R<RegistrationCreateResult> create(@Valid @RequestBody RegistrationCreateRequest req) {
        return R.ok(registrationService.create(req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPerm('reg:ticket:cancel')")
    @Idempotent
    @AuditLog(module = "registration", action = "退号", bizType = "reg_registration")
    public R<Void> cancel(@PathVariable Long id) {
        registrationService.cancel(id);
        return R.ok();
    }

    @GetMapping
    @PreAuthorize("@ss.hasPerm('reg:ticket:query')")
    public R<PageResult<RegistrationResponse>> page(RegistrationQuery query) {
        return R.ok(registrationService.page(query));
    }

    @GetMapping("/queue")
    @PreAuthorize("@ss.hasPerm('reg:ticket:query')")
    public R<List<RegistrationResponse>> queue(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate regDate,
            @RequestParam(required = false) Integer period) {
        return R.ok(registrationService.queue(doctorId, regDate, period));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('reg:ticket:query')")
    public R<RegistrationResponse> detail(@PathVariable Long id) {
        return R.ok(registrationService.detail(id));
    }
}
