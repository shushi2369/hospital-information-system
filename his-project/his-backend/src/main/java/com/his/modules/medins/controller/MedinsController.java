package com.his.modules.medins.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.medins.dto.ReconcileRequest;
import com.his.modules.medins.dto.SettleQuery;
import com.his.modules.medins.entity.MedinsSettle;
import com.his.modules.medins.service.MedinsService;
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
 * 医保对账接口（Y-01~Y-04）。
 */
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/medins")
@RequiredArgsConstructor
public class MedinsController {
    private final MedinsService medinsService;

    @PostMapping("/settles")
    @PreAuthorize("@ss.hasPerm('medins:settle:create')")
    @Idempotent
    @AuditLog(module = "medins", action = "医保申报", bizType = "medins_settle")
    public R<String> apply(@RequestParam Long billId,
                           @RequestParam(defaultValue = "1")
                           @org.springframework.validation.annotation.Validated
                           @jakarta.validation.constraints.Min(value = 1, message = "医保类型取值 1职工/2居民")
                           @jakarta.validation.constraints.Max(value = 2, message = "医保类型取值 1职工/2居民")
                           Integer insuranceType) {
        return R.ok(medinsService.apply(billId, insuranceType));
    }

    @GetMapping("/settles")
    @PreAuthorize("@ss.hasPerm('medins:settle:query')")
    public R<PageResult<MedinsSettle>> page(SettleQuery query) {
        return R.ok(medinsService.page(query));
    }

    @PostMapping("/settles/{id}/reconcile")
    @PreAuthorize("@ss.hasPerm('medins:settle:reconcile')")
    @Idempotent
    @AuditLog(module = "medins", action = "医保对账", bizType = "medins_settle")
    public R<Void> reconcile(@PathVariable Long id, @RequestBody(required = false) ReconcileRequest req) {
        medinsService.reconcile(id, req);
        return R.ok();
    }

    @GetMapping("/reconcile/daily")
    @PreAuthorize("@ss.hasPerm('medins:settle:query')")
    public R<List<MedinsSettle>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate settleDate) {
        return R.ok(medinsService.dailyReconcile(settleDate));
    }
}
