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

    /** 一百一十六轮 cdss【10】：命中处置回写（本人医嘱；ignored=false 表示提示后放弃开立） */
    @PostMapping("/hits/{id}/acknowledge")
    @PreAuthorize("@ss.hasPerm('cdss:hit:query')")
    @Idempotent
    public R<Void> acknowledge(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam boolean ignored) {
        cdssService.acknowledge(id, ignored);
        return R.ok();
    }

    @GetMapping("/hits")
    @PreAuthorize("@ss.hasPerm('cdss:hit:query')")
    public R<PageResult<CdssHit>> hits(CdssQuery query) {
        return R.ok(cdssService.hitPage(query));
    }

    /** 门诊处方 CDSS 轻量守门（一百四十九轮 D-14）：开方前确认弹窗数据源，提醒不阻断 */
    @GetMapping("/outpatient-check")
    @PreAuthorize("@ss.hasPerm('clinic:prescription:create')")
    public R<java.util.List<String>> outpatientCheck(
            @RequestParam(required = false) Long visitId,
            @RequestParam Long patientId,
            @RequestParam java.util.List<Long> drugIds) {
        // 一百六十四轮 D-48 契约注记：drugIds 当前仅透传（配伍/剂量引擎以住院侧为准，
        // 门诊用药核查 defer）——本端点现只产出患者过敏史告警，勿误依赖药品维度结果
        return R.ok(cdssService.checkOutpatientRx(visitId, patientId,
                com.his.infrastructure.security.CurrentUser.id(), drugIds));
    }
}
