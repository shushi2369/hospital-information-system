package com.his.modules.pharmacy.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.clinic.app.RxDisplayDTO;
import com.his.modules.pharmacy.dto.DispenseOrderQuery;
import com.his.modules.pharmacy.dto.DispenseOrderVO;
import com.his.modules.pharmacy.dto.ReturnOrderVO;
import com.his.modules.pharmacy.dto.ReturnQuery;
import com.his.modules.pharmacy.dto.ReturnRequest;
import com.his.modules.pharmacy.dto.ReviewRequest;
import com.his.modules.pharmacy.service.PharmacyService;
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

/**
 * 药房工作台接口（F-01~F-07）。
 */
@RestController
@RequestMapping("/api/v1/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {
    private final PharmacyService pharmacyService;

    @GetMapping("/prescriptions")
    @PreAuthorize("@ss.hasPerm('pharmacy:review:query')")
    public R<List<RxDisplayDTO>> reviewQueue() {
        return R.ok(pharmacyService.reviewQueue());
    }

    @PostMapping("/prescriptions/{id}/review")
    @PreAuthorize("@ss.hasPerm('pharmacy:review:do')")
    @Idempotent
    @AuditLog(module = "pharmacy", action = "处方审核", bizType = "cli_prescription")
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        pharmacyService.review(id, req);
        return R.ok();
    }

    @GetMapping("/prescriptions/dispensable")
    @PreAuthorize("@ss.hasPerm('pharmacy:dispense:query')")
    public R<List<RxDisplayDTO>> dispensableQueue() {
        return R.ok(pharmacyService.dispensableQueue());
    }

    @PostMapping("/prescriptions/{id}/dispense")
    @PreAuthorize("@ss.hasPerm('pharmacy:dispense:do')")
    @Idempotent
    @AuditLog(module = "pharmacy", action = "发药", bizType = "phr_dispense_order")
    public R<String> dispense(@PathVariable Long id) {
        return R.ok(pharmacyService.dispense(id));
    }

    @GetMapping("/dispense-orders")
    @PreAuthorize("@ss.hasPerm('pharmacy:dispense:query')")
    public R<PageResult<DispenseOrderVO>> dispenseOrders(DispenseOrderQuery query) {
        return R.ok(pharmacyService.dispenseOrderPage(query));
    }

    @PostMapping("/returns")
    @PreAuthorize("@ss.hasPerm('pharmacy:return:do')")
    @Idempotent
    @AuditLog(module = "pharmacy", action = "退药", bizType = "phr_return_order")
    public R<String> returnDrug(@Valid @RequestBody ReturnRequest req) {
        return R.ok(pharmacyService.returnDrug(req));
    }

    @GetMapping("/returns")
    @PreAuthorize("@ss.hasPerm('pharmacy:return:query')")
    public R<PageResult<ReturnOrderVO>> returns(ReturnQuery query) {
        return R.ok(pharmacyService.returnPage(query));
    }
}
