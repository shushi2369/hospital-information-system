package com.his.modules.whse.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.whse.dto.PurchaseOrderCreateRequest;
import com.his.modules.whse.dto.PurchaseOrderQuery;
import com.his.modules.whse.dto.ReceiveRequest;
import com.his.modules.whse.dto.SupplierRequest;
import com.his.modules.whse.entity.BasSupplier;
import com.his.modules.whse.entity.WhsePurchaseOrder;
import com.his.modules.whse.service.WhseService;
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
 * 药库管理接口（S-01~S-06）。
 */
@RestController
@RequestMapping("/api/v1/whse")
@RequiredArgsConstructor
public class WhseController {
    private final WhseService whseService;

    @PostMapping("/suppliers")
    @PreAuthorize("@ss.hasPerm('whse:supplier:manage')")
    @Idempotent
    @AuditLog(module = "whse", action = "新增供应商", bizType = "bas_supplier")
    public R<Long> createSupplier(@Valid @RequestBody SupplierRequest req) {
        return R.ok(whseService.createSupplier(req));
    }

    @GetMapping("/suppliers")
    @PreAuthorize("@ss.hasPerm('whse:supplier:manage')")
    public R<List<BasSupplier>> suppliers() {
        return R.ok(whseService.suppliers());
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("@ss.hasPerm('whse:po:query')")
    public R<PageResult<WhsePurchaseOrder>> page(PurchaseOrderQuery query) {
        return R.ok(whseService.page(query));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("@ss.hasPerm('whse:po:create')")
    @Idempotent
    @AuditLog(module = "whse", action = "创建采购单", bizType = "whse_purchase_order")
    public R<String> createPo(@Valid @RequestBody PurchaseOrderCreateRequest req) {
        return R.ok(whseService.createPo(req));
    }

    @PostMapping("/purchase-orders/{id}/approve")
    @PreAuthorize("@ss.hasPerm('whse:po:approve')")
    @Idempotent
    @AuditLog(module = "whse", action = "采购审批", bizType = "whse_purchase_order")
    public R<Void> approve(@PathVariable Long id) {
        whseService.approve(id);
        return R.ok();
    }

    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("@ss.hasPerm('whse:po:approve')")
    @Idempotent
    @AuditLog(module = "whse", action = "采购到货入库", bizType = "whse_purchase_order")
    public R<String> receive(@PathVariable Long id, @Valid @RequestBody ReceiveRequest req) {
        return R.ok(whseService.receive(id, req));
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("@ss.hasPerm('whse:po:create')")
    @Idempotent
    @AuditLog(module = "whse", action = "采购取消", bizType = "whse_purchase_order")
    public R<Void> cancel(@PathVariable Long id) {
        whseService.cancel(id);
        return R.ok();
    }
}
