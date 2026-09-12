package com.his.modules.pharmacy.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.pharmacy.dto.BatchQuery;
import com.his.modules.pharmacy.dto.BatchVO;
import com.his.modules.pharmacy.dto.InboundRequest;
import com.his.modules.pharmacy.dto.MovementQuery;
import com.his.modules.pharmacy.dto.WarningDTO;
import com.his.modules.pharmacy.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存管理接口（F-08~F-11）。
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping("/inbound")
    @PreAuthorize("@ss.hasPerm('inventory:inbound:create')")
    @Idempotent
    @AuditLog(module = "pharmacy", action = "入库", bizType = "inv_inbound_order")
    public R<String> inbound(@Valid @RequestBody InboundRequest req) {
        return R.ok(inventoryService.inbound(req));
    }

    @GetMapping("/batches")
    @PreAuthorize("@ss.hasPerm('inventory:query')")
    public R<PageResult<BatchVO>> batches(BatchQuery query) {
        return R.ok(inventoryService.batchPage(query));
    }

    @GetMapping("/warnings")
    @PreAuthorize("@ss.hasPerm('inventory:query')")
    public R<List<WarningDTO>> warnings() {
        return R.ok(inventoryService.warnings());
    }

    @GetMapping("/movements")
    @PreAuthorize("@ss.hasPerm('inventory:query')")
    public R<PageResult<BatchVO.Movement>> movements(MovementQuery query) {
        return R.ok(inventoryService.movementPage(query));
    }
}
