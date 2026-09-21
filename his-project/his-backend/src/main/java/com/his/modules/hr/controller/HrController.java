package com.his.modules.hr.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.hr.dto.*;
import com.his.modules.hr.entity.HrStaff;
import com.his.modules.hr.service.HrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** HR 人事接口（H-01~H-06，《19》§5.1）。 */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrController {
    private final HrService hrService;

    @GetMapping("/staff")
    @PreAuthorize("@ss.hasPerm('hr:staff:query')")
    public R<PageResult<HrStaff>> page(HrStaffQuery query) {
        return R.ok(hrService.page(query));
    }

    @GetMapping("/staff/{id}")
    @PreAuthorize("@ss.hasPerm('hr:staff:query')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(hrService.detail(id));
    }

    @PostMapping("/staff")
    @PreAuthorize("@ss.hasPerm('hr:staff:manage')")
    @Idempotent
    @AuditLog(module = "hr", action = "员工建档", bizType = "hr_staff")
    public R<String> create(@Valid @RequestBody StaffUpsertRequest req) {
        return R.ok(hrService.create(req));
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize("@ss.hasPerm('hr:staff:manage')")
    @Idempotent
    @AuditLog(module = "hr", action = "员工更新", bizType = "hr_staff")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody StaffUpsertRequest req) {
        hrService.update(id, req);
        return R.ok();
    }

    @PostMapping("/staff/{id}/exit")
    @PreAuthorize("@ss.hasPerm('hr:staff:manage')")
    @Idempotent
    @AuditLog(module = "hr", action = "离职登记", bizType = "hr_staff")
    public R<Void> exit(@PathVariable Long id) {
        hrService.exit(id);
        return R.ok();
    }

    @PostMapping("/staff/{id}/title-change")
    @PreAuthorize("@ss.hasPerm('hr:staff:manage')")
    @Idempotent
    @AuditLog(module = "hr", action = "职称变更", bizType = "hr_title_change")
    public R<Long> titleChange(@PathVariable Long id, @Valid @RequestBody TitleChangeRequest req) {
        return R.ok(hrService.titleChange(id, req));
    }
}
