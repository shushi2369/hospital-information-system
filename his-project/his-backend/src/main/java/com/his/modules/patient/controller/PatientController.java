package com.his.modules.patient.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.patient.dto.PatientCreateRequest;
import com.his.modules.patient.dto.PatientQuery;
import com.his.modules.patient.dto.PatientResponse;
import com.his.modules.patient.dto.PatientUpdateRequest;
import com.his.modules.patient.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者中心接口（P-01~P-05）。
 */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientService patientService;

    @PostMapping
    @PreAuthorize("@ss.hasPerm('patient:archive:create')")
    @Idempotent
    @AuditLog(module = "patient", action = "患者建档", bizType = "pat_patient")
    public R<String> create(@Valid @RequestBody PatientCreateRequest req) {
        return R.ok(patientService.create(req));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPerm('patient:archive:query')")
    public R<PageResult<PatientResponse>> page(PatientQuery query) {
        return R.ok(patientService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('patient:archive:query')")
    public R<PatientResponse> detail(@PathVariable Long id) {
        return R.ok(patientService.detail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('patient:archive:update')")
    @Idempotent
    @AuditLog(module = "patient", action = "患者档案修改", bizType = "pat_patient")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PatientUpdateRequest req) {
        patientService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/cards")
    @PreAuthorize("@ss.hasPerm('patient:archive:update')")
    @Idempotent
    @AuditLog(module = "patient", action = "补办就诊卡", bizType = "pat_medical_card")
    public R<String> addCard(@PathVariable Long id) {
        return R.ok(patientService.addCard(id));
    }
}
