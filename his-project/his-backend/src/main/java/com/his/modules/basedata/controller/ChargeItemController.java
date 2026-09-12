package com.his.modules.basedata.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.modules.basedata.dto.ChargeItemCreateRequest;
import com.his.modules.basedata.dto.ChargeItemQueryRequest;
import com.his.modules.basedata.dto.ChargeItemUpdateRequest;
import com.his.modules.basedata.entity.ChargeItem;
import com.his.modules.basedata.service.ChargeItemService;
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
 * 基础资料 - 收费项目管理（D-12/D-13/D-14，/api/v1/basedata/charge-items）。
 */
@RestController
@RequestMapping("/api/v1/basedata")
@RequiredArgsConstructor
public class ChargeItemController {

    private final ChargeItemService chargeItemService;

    /**
     * D-12 收费项目列表（按类别/状态过滤，下拉与列表共用）。
     */
    @GetMapping("/charge-items")
    @PreAuthorize("@ss.hasPerm('basedata:item:query')")
    public R<List<ChargeItem>> list(ChargeItemQueryRequest query) {
        return R.ok(chargeItemService.list(query));
    }

    /**
     * D-13 新增收费项目（幂等 + 审计）。
     */
    @PostMapping("/charge-items")
    @PreAuthorize("@ss.hasPerm('basedata:item:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "新增收费项目", bizType = "charge_item")
    public R<ChargeItem> create(@Valid @RequestBody ChargeItemCreateRequest request) {
        return R.ok(chargeItemService.create(request));
    }

    /**
     * D-14 修改/调价/停用收费项目（幂等 + 审计）。
     */
    @PutMapping("/charge-items/{id}")
    @PreAuthorize("@ss.hasPerm('basedata:item:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "修改收费项目", bizType = "charge_item")
    public R<ChargeItem> update(@PathVariable Long id, @Valid @RequestBody ChargeItemUpdateRequest request) {
        return R.ok(chargeItemService.update(id, request));
    }
}
