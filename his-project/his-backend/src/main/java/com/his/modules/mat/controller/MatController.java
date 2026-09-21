package com.his.modules.mat.controller;

import com.his.common.*;
import com.his.modules.mat.dto.*;
import com.his.modules.mat.entity.*;
import com.his.modules.mat.service.MatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 物资耗材接口（M-01~M-09，《19》§5.2）。 */
@RestController
@RequestMapping("/api/v1/mat")
@RequiredArgsConstructor
public class MatController {
    private final MatService matService;

    @GetMapping("/materials")
    @PreAuthorize("@ss.hasPerm('mat:stock:query') or @ss.hasPerm('mat:material:manage')")
    public R<PageResult<Map<String, Object>>> materialPage(MatQuery query) {
        return R.ok(matService.materialPage(query));
    }

    @PostMapping("/materials")
    @PreAuthorize("@ss.hasPerm('mat:material:manage')")
    @Idempotent
    @AuditLog(module = "mat", action = "物资建档", bizType = "mat_material")
    public R<String> createMaterial(@Valid @RequestBody MatMaterial material) {
        return R.ok(matService.createMaterial(material));
    }

    @GetMapping("/stocks")
    @PreAuthorize("@ss.hasPerm('mat:stock:query')")
    public R<List<MatStock>> stocks() {
        return R.ok(matService.stocks());
    }

    @GetMapping("/purchases")
    @PreAuthorize("@ss.hasPerm('mat:purchase:query')")
    public R<PageResult<MatPurchase>> purchasePage(MatQuery query) {
        return R.ok(matService.purchasePage(query));
    }

    @PostMapping("/purchases")
    @PreAuthorize("@ss.hasPerm('mat:purchase:create')")
    @Idempotent
    @AuditLog(module = "mat", action = "创建采购单", bizType = "mat_purchase")
    public R<String> createPurchase(@Valid @RequestBody MatPurchaseRequest req) {
        return R.ok(matService.createPurchase(req));
    }

    @PostMapping("/purchases/{id}/approve")
    @PreAuthorize("@ss.hasPerm('mat:purchase:approve')")
    @Idempotent
    @AuditLog(module = "mat", action = "采购审批", bizType = "mat_purchase")
    public R<Void> approve(@PathVariable Long id) {
        matService.approve(id);
        return R.ok();
    }

    @PostMapping("/purchases/{id}/receive")
    @PreAuthorize("@ss.hasPerm('mat:purchase:approve')")
    @Idempotent
    @AuditLog(module = "mat", action = "到货入库", bizType = "mat_purchase")
    public R<Void> receive(@PathVariable Long id) {
        matService.receive(id);
        return R.ok();
    }

    @PostMapping("/purchases/{id}/cancel")
    @PreAuthorize("@ss.hasPerm('mat:purchase:create')")
    @Idempotent
    @AuditLog(module = "mat", action = "取消采购单", bizType = "mat_purchase")
    public R<Void> cancel(@PathVariable Long id) {
        matService.cancel(id);
        return R.ok();
    }

    @GetMapping("/requisitions")
    @PreAuthorize("@ss.hasPerm('mat:requisition:query')")
    public R<PageResult<MatRequisition>> requisitionPage(MatQuery query) {
        return R.ok(matService.requisitionPage(query));
    }

    @PostMapping("/requisitions")
    @PreAuthorize("@ss.hasPerm('mat:requisition:create')")
    @Idempotent
    @AuditLog(module = "mat", action = "科室领用", bizType = "mat_requisition")
    public R<String> requisition(@Valid @RequestBody MatRequisitionRequest req) {
        return R.ok(matService.requisition(req));
    }
}
