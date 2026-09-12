package com.his.modules.basedata.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.modules.basedata.dto.DoctorCreateRequest;
import com.his.modules.basedata.dto.DoctorQueryRequest;
import com.his.modules.basedata.dto.DoctorUpdateRequest;
import com.his.modules.basedata.entity.Doctor;
import com.his.modules.basedata.service.DoctorService;
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

import java.util.List;

/**
 * 基础资料 - 医生管理（D-06/D-07/D-08，/api/v1/basedata/doctors）。
 */
@RestController
@RequestMapping("/api/v1/basedata")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    /**
     * D-06 医生列表（按科室/是否专家过滤，下拉与列表共用）。
     */
    @GetMapping("/doctors")
    @PreAuthorize("@ss.hasPerm('basedata:doctor:query')")
    public R<List<Doctor>> list(DoctorQueryRequest query) {
        return R.ok(doctorService.list(query));
    }

    /**
     * D-07 新增医生（绑定用户账号，幂等 + 审计）。
     */
    @PostMapping("/doctors")
    @PreAuthorize("@ss.hasPerm('basedata:doctor:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "新增医生", bizType = "doctor")
    public R<Doctor> create(@Valid @RequestBody DoctorCreateRequest request) {
        return R.ok(doctorService.create(request));
    }

    /**
     * D-08 修改医生（职称/号费/限额/停用，幂等 + 审计）。
     */
    @PutMapping("/doctors/{id}")
    @PreAuthorize("@ss.hasPerm('basedata:doctor:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "修改医生", bizType = "doctor")
    public R<Doctor> update(@PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest request) {
        return R.ok(doctorService.update(id, request));
    }
}
