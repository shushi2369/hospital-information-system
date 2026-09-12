package com.his.modules.basedata.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.basedata.dto.DrugCreateRequest;
import com.his.modules.basedata.dto.DrugQueryRequest;
import com.his.modules.basedata.dto.DrugUpdateRequest;
import com.his.modules.basedata.entity.Drug;
import com.his.modules.basedata.service.DrugService;
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

/**
 * 基础资料 - 药品管理（D-09/D-10/D-11，/api/v1/basedata/drugs）。
 */
@RestController
@RequestMapping("/api/v1/basedata")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    /**
     * D-09 药品分页查询（名称/编码/分类/状态过滤）。
     */
    @GetMapping("/drugs")
    @PreAuthorize("@ss.hasPerm('basedata:drug:query')")
    public R<PageResult<Drug>> page(DrugQueryRequest query) {
        return R.ok(drugService.page(query));
    }

    /**
     * D-10 新增药品（幂等 + 审计）。
     */
    @PostMapping("/drugs")
    @PreAuthorize("@ss.hasPerm('basedata:drug:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "新增药品", bizType = "drug")
    public R<Drug> create(@Valid @RequestBody DrugCreateRequest request) {
        return R.ok(drugService.create(request));
    }

    /**
     * D-11 修改/调价/停用药品（幂等 + 审计）。
     */
    @PutMapping("/drugs/{id}")
    @PreAuthorize("@ss.hasPerm('basedata:drug:manage')")
    @Idempotent
    @AuditLog(module = "basedata", action = "修改药品", bizType = "drug")
    public R<Drug> update(@PathVariable Long id, @Valid @RequestBody DrugUpdateRequest request) {
        return R.ok(drugService.update(id, request));
    }
}
