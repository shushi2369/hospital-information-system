package com.his.modules.pe.controller;

import com.his.common.*;
import com.his.modules.pe.dto.*;
import com.his.modules.pe.entity.*;
import com.his.modules.pe.service.PeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 体检接口（P-01~P-08，《19》§5.3）。 */
@RestController
@RequestMapping("/api/v1/pe")
@RequiredArgsConstructor
public class PeController {
    private final PeService peService;

    @GetMapping("/packages")
    @PreAuthorize("@ss.hasPerm('pe:record:query') or @ss.hasPerm('pe:package:manage')")
    public R<PageResult<PePackage>> packages(com.his.common.PageQuery query) {
        return R.ok(peService.packagePage(query));
    }

    @PostMapping("/packages")
    @PreAuthorize("@ss.hasPerm('pe:package:manage')")
    @Idempotent
    @AuditLog(module = "pe", action = "创建体检套餐", bizType = "pe_package")
    public R<String> createPackage(@Valid @RequestBody PePackageRequest req) {
        return R.ok(peService.createPackage(req));
    }

    @PutMapping("/packages/{id}")
    @PreAuthorize("@ss.hasPerm('pe:package:manage')")
    @Idempotent
    @AuditLog(module = "pe", action = "套餐更新", bizType = "pe_package")
    public R<Void> updatePackage(@PathVariable Long id, @Valid @RequestBody PePackageRequest req) {
        peService.updatePackage(id, req);
        return R.ok();
    }

    @GetMapping("/records")
    @PreAuthorize("@ss.hasPerm('pe:record:query')")
    public R<PageResult<PeRecord>> records(PeRecordQuery query) {
        return R.ok(peService.recordPage(query));
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("@ss.hasPerm('pe:record:query')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(peService.detail(id));
    }

    @PostMapping("/records")
    @PreAuthorize("@ss.hasPerm('pe:record:create')")
    @Idempotent
    @AuditLog(module = "pe", action = "体检登记", bizType = "pe_record")
    public R<String> register(@Valid @RequestBody PeRegisterRequest req) {
        return R.ok(peService.register(req));
    }

    @PostMapping("/records/{id}/start")
    @PreAuthorize("@ss.hasPerm('pe:record:manage')")
    @Idempotent
    @AuditLog(module = "pe", action = "开始体检", bizType = "pe_record")
    public R<Void> start(@PathVariable Long id) {
        peService.start(id);
        return R.ok();
    }

    @PostMapping("/records/{id}/results")
    @PreAuthorize("@ss.hasPerm('pe:result:entry')")
    @Idempotent
    @AuditLog(module = "pe", action = "分项结果录入", bizType = "pe_result")
    public R<Long> saveResult(@PathVariable Long id, @Valid @RequestBody PeResultRequest req) {
        return R.ok(peService.saveResult(id, req));
    }

    @PostMapping("/records/{id}/finish")
    @PreAuthorize("@ss.hasPerm('pe:record:manage')")
    @Idempotent
    @AuditLog(module = "pe", action = "完成体检", bizType = "pe_record")
    public R<Void> finish(@PathVariable Long id) {
        peService.finish(id);
        return R.ok();
    }

    @PostMapping("/records/{id}/report")
    @PreAuthorize("@ss.hasPerm('pe:report:publish')")
    @Idempotent
    @AuditLog(module = "pe", action = "总检报告发布", bizType = "pe_report")
    public R<String> publishReport(@PathVariable Long id, @Valid @RequestBody PeReportRequest req) {
        return R.ok(peService.publishReport(id, req));
    }
}
