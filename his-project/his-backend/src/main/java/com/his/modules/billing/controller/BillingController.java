package com.his.modules.billing.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.billing.dto.BillQuery;
import com.his.modules.billing.dto.BillResponse;
import com.his.modules.billing.dto.ChargeRequest;
import com.his.modules.billing.dto.PayableResultDTO;
import com.his.modules.billing.dto.RefundQuery;
import com.his.modules.billing.dto.RefundRequest;
import com.his.modules.billing.dto.SettlementQuery;
import com.his.modules.billing.dto.SettlementRequest;
import com.his.modules.billing.entity.BilDailySettlement;
import com.his.modules.billing.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 收费结算接口（B-01~B-08）。
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService billingService;

    @GetMapping("/visits/unpaid")
    @PreAuthorize("@ss.hasPerm('billing:charge:query')")
    public R<List<Map<String, Object>>> unpaidVisits() {
        return R.ok(billingService.unpaidVisits());
    }

    @GetMapping("/visits/{visitId}/payable")
    @PreAuthorize("@ss.hasPerm('billing:charge:query')")
    public R<PayableResultDTO> payable(@PathVariable Long visitId) {
        return R.ok(billingService.payable(visitId));
    }

    @PostMapping("/bills")
    @PreAuthorize("@ss.hasPerm('billing:charge:create')")
    @Idempotent
    @AuditLog(module = "billing", action = "收费", bizType = "bil_charge_bill")
    public R<BillResponse> charge(@Valid @RequestBody ChargeRequest req) {
        return R.ok(billingService.charge(req));
    }

    @GetMapping("/bills")
    @PreAuthorize("@ss.hasPerm('billing:charge:query')")
    public R<PageResult<BillResponse>> bills(BillQuery query) {
        return R.ok(billingService.billPage(query));
    }

    @GetMapping("/bills/{id}")
    @PreAuthorize("@ss.hasPerm('billing:charge:query')")
    public R<BillResponse> billDetail(@PathVariable Long id) {
        return R.ok(billingService.billDetail(id));
    }

    @PostMapping("/refunds")
    @PreAuthorize("@ss.hasPerm('billing:refund:create')")
    @Idempotent
    @AuditLog(module = "billing", action = "退费", bizType = "bil_refund_bill")
    public R<String> refund(@Valid @RequestBody RefundRequest req) {
        return R.ok(billingService.refund(req));
    }

    @GetMapping("/refunds")
    @PreAuthorize("@ss.hasPerm('billing:refund:query')")
    public R<PageResult<BillResponse.Refund>> refunds(RefundQuery query) {
        return R.ok(billingService.refundPage(query));
    }

    @PostMapping("/settlements")
    @PreAuthorize("@ss.hasPerm('billing:settle:do')")
    @Idempotent
    @AuditLog(module = "billing", action = "日结", bizType = "bil_daily_settlement")
    public R<BilDailySettlement> settle(@Valid @RequestBody SettlementRequest req) {
        return R.ok(billingService.settle(req));
    }

    @GetMapping("/settlements")
    @PreAuthorize("@ss.hasPerm('billing:settle:query')")
    public R<PageResult<BilDailySettlement>> settlements(SettlementQuery query) {
        return R.ok(billingService.settlementPage(query));
    }
}
