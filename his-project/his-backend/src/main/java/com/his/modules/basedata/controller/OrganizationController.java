package com.his.modules.basedata.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.modules.basedata.dto.OrganizationUpdateRequest;
import com.his.modules.basedata.entity.Organization;
import com.his.modules.basedata.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础资料 - 组织机构（D-01/D-02，/api/v1/basedata/org）。
 */
@RestController
@RequestMapping("/api/v1/basedata")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * D-01 机构信息查询。
     */
    @GetMapping("/org")
    @PreAuthorize("@ss.hasPerm('basedata:org:query')")
    public R<Organization> get() {
        return R.ok(organizationService.get());
    }

    /**
     * D-02 机构信息维护（幂等 + 审计）。
     */
    @PutMapping("/org")
    @PreAuthorize("@ss.hasPerm('basedata:org:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "机构维护", bizType = "organization")
    public R<Organization> update(@Valid @RequestBody OrganizationUpdateRequest request) {
        return R.ok(organizationService.update(request));
    }
}
