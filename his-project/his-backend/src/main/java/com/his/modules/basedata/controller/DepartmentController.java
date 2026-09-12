package com.his.modules.basedata.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.modules.basedata.dto.DepartmentCreateRequest;
import com.his.modules.basedata.dto.DepartmentQueryRequest;
import com.his.modules.basedata.dto.DepartmentUpdateRequest;
import com.his.modules.basedata.entity.Department;
import com.his.modules.basedata.service.DepartmentService;
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
 * 基础资料 - 科室管理（D-03/D-04/D-05，/api/v1/basedata/departments）。
 */
@RestController
@RequestMapping("/api/v1/basedata")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * D-03 科室列表（按类型/状态过滤，下拉与列表共用）。
     */
    @GetMapping("/departments")
    @PreAuthorize("@ss.hasPerm('basedata:dept:query')")
    public R<List<Department>> list(DepartmentQueryRequest query) {
        return R.ok(departmentService.list(query));
    }

    /**
     * D-04 新增科室（幂等 + 审计）。
     */
    @PostMapping("/departments")
    @PreAuthorize("@ss.hasPerm('basedata:dept:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "新增科室", bizType = "department")
    public R<Department> create(@Valid @RequestBody DepartmentCreateRequest request) {
        return R.ok(departmentService.create(request));
    }

    /**
     * D-05 修改/停用科室（幂等 + 审计）。
     */
    @PutMapping("/departments/{id}")
    @PreAuthorize("@ss.hasPerm('basedata:dept:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "修改科室", bizType = "department")
    public R<Department> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        return R.ok(departmentService.update(id, request));
    }
}
