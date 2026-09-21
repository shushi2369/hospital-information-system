package com.his.modules.cdss.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.cdss.dto.CdssQuery;
import com.his.modules.cdss.entity.CdssHit;
import com.his.modules.cdss.entity.CdssRule;
import com.his.modules.cdss.service.CdssService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CDSS 接口（C-01~C-02，《19》§5.4）。check 为 doc 内部 hook，无独立 REST 入口。 */
@RestController
@RequestMapping("/api/v1/cdss")
@RequiredArgsConstructor
public class CdssController {
    private final CdssService cdssService;

    @GetMapping("/rules")
    @PreAuthorize("@ss.hasPerm('cdss:rule:manage')")
    public R<PageResult<CdssRule>> rules(CdssQuery query) {
        return R.ok(cdssService.rulePage(query));
    }

    @PostMapping("/rules")
    @PreAuthorize("@ss.hasPerm('cdss:rule:manage')")
    @Idempotent
    @AuditLog(module = "cdss", action = "创建规则", bizType = "cdss_rule")
    public R<String> createRule(@RequestBody CdssRule rule) {
        return R.ok(cdssService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("@ss.hasPerm('cdss:rule:manage')")
    @Idempotent
    @AuditLog(module = "cdss", action = "更新规则", bizType = "cdss_rule")
    public R<Void> updateRule(@PathVariable Long id, @RequestBody CdssRule req) {
        cdssService.updateRule(id, req);
        return R.ok();
    }

    @GetMapping("/hits")
    @PreAuthorize("@ss.hasPerm('cdss:hit:query')")
    public R<PageResult<CdssHit>> hits(CdssQuery query) {
        return R.ok(cdssService.hitPage(query));
    }
}
