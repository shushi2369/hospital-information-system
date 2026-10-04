package com.his.modules.inp.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.inp.dto.AdmissionCreateRequest;
import com.his.modules.inp.dto.AdmissionQuery;
import com.his.modules.inp.dto.AdmissionResponse;
import com.his.modules.inp.dto.BedVO;
import com.his.modules.inp.dto.DepositRefundRequest;
import com.his.modules.inp.dto.DepositRequest;
import com.his.modules.inp.dto.DischargeRequest;
import com.his.modules.inp.dto.FeeGroupResponse;
import com.his.modules.inp.dto.ManualFeeRequest;
import com.his.modules.inp.dto.TransferRequest;
import com.his.modules.inp.service.InpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院管理接口（I-01~I-11；出院结算 I-12 在收费控制器，复用 billing 权限）。
 */
@RestController
@RequestMapping("/api/v1/inp")
@RequiredArgsConstructor
public class InpController {
    private final InpService inpService;

    @GetMapping("/wards")
    @PreAuthorize("@ss.hasPerm('inp:bed:query')")
    public R<List<com.his.modules.inp.entity.InpWard>> wards() {
        return R.ok(inpService.wards());
    }

    @GetMapping("/beds")
    @PreAuthorize("@ss.hasPerm('inp:bed:query')")
    public R<List<BedVO>> beds(
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) Integer bedStatus) {
        return R.ok(inpService.beds(wardId, bedStatus));
    }

    @PostMapping("/beds")
    @PreAuthorize("@ss.hasPerm('inp:bed:manage')")
    @Idempotent
    @AuditLog(module = "inp", action = "新增床位", bizType = "inp_bed")
    public R<Long> createBed(@Valid @RequestBody com.his.modules.inp.dto.BedCreateRequest req) {
        return R.ok(inpService.createBed(req));
    }

    @PutMapping("/beds/{id}/status")
    @PreAuthorize("@ss.hasPerm('inp:bed:manage')")
    @Idempotent
    @AuditLog(module = "inp", action = "床位状态调整", bizType = "inp_bed")
    public R<Void> updateBedStatus(@PathVariable Long id,
                                   @Valid @RequestBody com.his.modules.inp.dto.BedStatusRequest req) {
        inpService.updateBedStatus(id, req.getBedStatus());
        return R.ok();
    }

    @PostMapping("/admissions")
    @PreAuthorize("@ss.hasPerm('inp:admission:create')")
    @Idempotent
    @AuditLog(module = "inp", action = "入院登记", bizType = "inp_admission")
    public R<AdmissionResponse> create(@Valid @RequestBody AdmissionCreateRequest req) {
        return R.ok(inpService.createAdmission(req));
    }

    @GetMapping("/admissions")
    @PreAuthorize("@ss.hasPerm('inp:admission:query')")
    public R<PageResult<AdmissionResponse>> page(AdmissionQuery query) {
        return R.ok(inpService.page(query));
    }

    @GetMapping("/admissions/{id}")
    @PreAuthorize("@ss.hasPerm('inp:admission:query')")
    public R<AdmissionResponse> detail(@PathVariable Long id) {
        return R.ok(inpService.detail(id));
    }

    @PostMapping("/admissions/{id}/transfers")
    @PreAuthorize("@ss.hasPerm('inp:transfer:create')")
    @Idempotent
    @AuditLog(module = "inp", action = "转科", bizType = "inp_admission")
    public R<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest req) {
        inpService.transfer(id, req);
        return R.ok();
    }

    @PostMapping("/admissions/{id}/deposits")
    @PreAuthorize("@ss.hasPerm('inp:deposit:create')")
    @Idempotent
    @AuditLog(module = "inp", action = "缴纳押金", bizType = "inp_deposit")
    public R<BigDecimal> deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest req) {
        return R.ok(inpService.addDeposit(id, req));
    }

    /** 退押金（七十轮）：仅已结算住院，上限 = 押金余额 - 账单额（应退口径） */
    @PostMapping("/admissions/{id}/deposit-refunds")
    @PreAuthorize("@ss.hasPerm('inp:deposit:refund')")
    @Idempotent
    @AuditLog(module = "inp", action = "退押金", bizType = "inp_deposit")
    public R<BigDecimal> refundDeposit(@PathVariable Long id, @Valid @RequestBody DepositRefundRequest req) {
        return R.ok(inpService.refundDeposit(id, req));
    }

    @GetMapping("/admissions/{id}/daily-fees")
    @PreAuthorize("@ss.hasPerm('inp:fee:query')")
    public R<List<FeeGroupResponse>> dailyFees(@PathVariable Long id) {
        return R.ok(inpService.dailyFees(id));
    }

    @PostMapping("/daily-fees/manual")
    @PreAuthorize("@ss.hasPerm('inp:fee:create')")
    @Idempotent
    @AuditLog(module = "inp", action = "手工记账", bizType = "inp_daily_fee")
    public R<Long> manualFee(@Valid @RequestBody ManualFeeRequest req,
                             @RequestParam Long admissionId) {
        return R.ok(inpService.addManualFee(admissionId, req));
    }

    @PostMapping("/admissions/{id}/discharge")
    @PreAuthorize("@ss.hasPerm('inp:discharge:create')")
    @Idempotent
    @AuditLog(module = "inp", action = "出院申请", bizType = "inp_admission")
    public R<Void> discharge(@PathVariable Long id, @Valid @RequestBody DischargeRequest req) {
        inpService.discharge(id, req);
        return R.ok();
    }
}
