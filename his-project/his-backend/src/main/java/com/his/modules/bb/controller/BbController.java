package com.his.modules.bb.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.bb.dto.*;
import com.his.modules.bb.entity.*;
import com.his.modules.bb.service.BbService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 输血闭环接口（B-01~B-12，《22》§5.1）。 */
@RestController
@RequestMapping("/api/v1/bb")
@RequiredArgsConstructor
public class BbController {
    private final BbService bbService;

    @GetMapping("/bags")
    @PreAuthorize("@ss.hasPerm('bb:bags:manage') or @ss.hasPerm('bb:request:query')")
    public R<PageResult<BbBloodBag>> bags(com.his.common.PageQuery query,
            @RequestParam(required = false) Integer bloodType,
            @RequestParam(required = false) Integer component,
            @RequestParam(required = false) Integer status) {
        return R.ok(bbService.bagPage(query, bloodType, component, status));
    }

    @GetMapping("/bags/available")
    @PreAuthorize("@ss.hasPerm('bb:bags:manage') or @ss.hasPerm('bb:cross:match')")
    public R<List<BbBloodBag>> available(@RequestParam(required = false) Integer bloodType,
            @RequestParam(required = false) Integer component) {
        return R.ok(bbService.availableBags(bloodType, component));
    }

    @PostMapping("/bags")
    @PreAuthorize("@ss.hasPerm('bb:bags:manage')")
    @Idempotent
    @AuditLog(module = "bb", action = "血袋入库", bizType = "bb_blood_bag")
    public R<String> createBag(@Valid @RequestBody BbBagRequest req) {
        return R.ok(bbService.createBag(req));
    }

    @PostMapping("/bags/{id}/scraps")
    @PreAuthorize("@ss.hasPerm('bb:bags:manage')")
    @Idempotent
    @AuditLog(module = "bb", action = "血袋报废", bizType = "bb_blood_bag")
    public R<Void> scrapBag(@PathVariable Long id) {
        bbService.scrapBag(id);
        return R.ok();
    }

    @PostMapping("/requests")
    @PreAuthorize("@ss.hasPerm('bb:request:create')")
    @Idempotent
    @AuditLog(module = "bb", action = "用血申请", bizType = "bb_request")
    public R<String> createRequest(@Valid @RequestBody BbRequestCreateRequest req) {
        return R.ok(bbService.createRequest(req));
    }

    @GetMapping("/requests")
    @PreAuthorize("@ss.hasPerm('bb:request:query')")
    public R<PageResult<BbRequest>> requests(BbRequestQuery query) {
        return R.ok(bbService.requestPage(query));
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("@ss.hasPerm('bb:request:query')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(bbService.detail(id));
    }

    @PostMapping("/requests/{id}/review")
    @PreAuthorize("@ss.hasPerm('bb:request:review')")
    @Idempotent
    @AuditLog(module = "bb", action = "用血审核", bizType = "bb_request")
    public R<Void> review(@PathVariable Long id, @RequestParam boolean approved,
            @RequestParam(required = false) String note) {
        bbService.review(id, approved, note);
        return R.ok();
    }

    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("@ss.hasPerm('bb:request:create')")
    @Idempotent
    @AuditLog(module = "bb", action = "取消用血申请", bizType = "bb_request")
    public R<Void> cancel(@PathVariable Long id) {
        bbService.cancel(id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/cross-match")
    @PreAuthorize("@ss.hasPerm('bb:cross:match')")
    @Idempotent
    @AuditLog(module = "bb", action = "交叉配血", bizType = "bb_cross_match")
    public R<Long> crossMatch(@PathVariable Long id, @Valid @RequestBody CrossMatchRequest req) {
        return R.ok(bbService.crossMatch(id, req));
    }

    @PostMapping("/requests/{id}/issue")
    @PreAuthorize("@ss.hasPerm('bb:issue:manage')")
    @Idempotent
    @AuditLog(module = "bb", action = "发血", bizType = "bb_issue")
    public R<Long> issue(@PathVariable Long id, @Valid @RequestBody IssueRequest req) {
        return R.ok(bbService.issue(id, req));
    }

    @PostMapping("/requests/{id}/transfusion")
    @PreAuthorize("@ss.hasPerm('bb:transfusion:execute')")
    @Idempotent
    @AuditLog(module = "bb", action = "输血开始", bizType = "bb_transfusion")
    public R<Long> startTransfusion(@PathVariable Long id, @Valid @RequestBody TransfusionStartRequest req) {
        return R.ok(bbService.startTransfusion(id, req));
    }

    @PostMapping("/requests/{id}/finish")
    @PreAuthorize("@ss.hasPerm('bb:transfusion:execute')")
    @Idempotent
    @AuditLog(module = "bb", action = "输血结束", bizType = "bb_transfusion")
    public R<Void> finishTransfusion(@PathVariable Long id, @Valid @RequestBody TransfusionFinishRequest req) {
        bbService.finishTransfusion(id, req);
        return R.ok();
    }

    @PostMapping("/requests/{id}/adverse")
    @PreAuthorize("@ss.hasPerm('bb:adverse:report')")
    @Idempotent
    @AuditLog(module = "bb", action = "不良反应登记", bizType = "bb_adverse")
    public R<Long> adverse(@PathVariable Long id, @Valid @RequestBody AdverseRequest req) {
        return R.ok(bbService.adverse(id, req));
    }
}
