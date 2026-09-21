package com.his.modules.pub.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.pub.dto.ReceiptRequest;
import com.his.modules.pub.entity.PubHaiCase;
import com.his.modules.pub.entity.PubInfectiousCard;
import com.his.modules.pub.service.PubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 公共卫生接口（传染病报告卡 + 院感，四期三批）。 */
@RestController
@RequestMapping("/api/v1/pub")
@RequiredArgsConstructor
public class PubController {
    private final PubService pubService;

    @GetMapping("/cards")
    @PreAuthorize("@ss.hasPerm('pub:card:query')")
    public R<PageResult<PubInfectiousCard>> cards(com.his.common.PageQuery query,
            @RequestParam(required = false) Integer status) {
        return R.ok(pubService.cardPage(query, status));
    }

    @PostMapping("/cards/{id}/report")
    @PreAuthorize("@ss.hasPerm('pub:card:report')")
    @Idempotent
    @AuditLog(module = "pub", action = "传染病上报登记", bizType = "pub_infectious_card")
    public R<Void> report(@PathVariable Long id) {
        pubService.report(id);
        return R.ok();
    }

    @PostMapping("/cards/{id}/approve")
    @PreAuthorize("@ss.hasPerm('pub:card:report')")
    @Idempotent
    @AuditLog(module = "pub", action = "传染病审核", bizType = "pub_infectious_card")
    public R<Void> approve(@PathVariable Long id) {
        pubService.approve(id);
        return R.ok();
    }

    @PostMapping("/cards/{id}/receipt")
    @PreAuthorize("@ss.hasPerm('pub:card:receipt')")
    @Idempotent
    @AuditLog(module = "pub", action = "疾控回执登记", bizType = "pub_infectious_card")
    public R<Void> receipt(@PathVariable Long id, @Valid @RequestBody ReceiptRequest req) {
        pubService.receipt(id, req);
        return R.ok();
    }

    @PostMapping("/hai")
    @PreAuthorize("@ss.hasPerm('pub:hai:confirm') or @ss.hasPerm('clinic:exam:create')")
    @Idempotent
    @AuditLog(module = "pub", action = "院感病例上报", bizType = "pub_hai_case")
    public R<String> haiReport(@Valid @RequestBody PubHaiCase req) {
        req.setInfectionSite(req.getInfectionSite());
        return R.ok(pubService.haiReport(req));
    }

    @PostMapping("/hai/{id}/confirm")
    @PreAuthorize("@ss.hasPerm('pub:hai:confirm')")
    @Idempotent
    @AuditLog(module = "pub", action = "院感确认", bizType = "pub_hai_case")
    public R<Void> haiConfirm(@PathVariable Long id, @RequestParam Integer targetStatus,
            @RequestParam(required = false) String note) {
        pubService.haiConfirm(id, note, targetStatus);
        return R.ok();
    }
}
