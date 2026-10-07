package com.his.modules.cnt.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.cnt.entity.CntRequest;
import com.his.modules.cnt.service.CntService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 会诊接口（T-01~04，四期三批）。 */
@RestController
@RequestMapping("/api/v1/cnt")
@RequiredArgsConstructor
public class CntController {
    private final CntService cntService;

    @PostMapping("/requests")
    @PreAuthorize("@ss.hasPerm('cnt:request')")
    @Idempotent
    @AuditLog(module = "cnt", action = "会诊申请", bizType = "cnt_request")
    public R<String> create(@RequestBody CntRequest req) {
        return R.ok(cntService.create(req));
    }

    @GetMapping("/requests")
    @PreAuthorize("@ss.hasPerm('cnt:query')")
    public R<PageResult<CntRequest>> page(com.his.common.PageQuery query,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer status) {
        return R.ok(cntService.page(query, patientId, status));
    }

    @PostMapping("/requests/{id}/accept")
    @PreAuthorize("@ss.hasPerm('cnt:execute')")
    @Idempotent
    @AuditLog(module = "cnt", action = "会诊接受", bizType = "cnt_request")
    public R<Void> accept(@PathVariable Long id) {
        cntService.accept(id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/complete")
    @PreAuthorize("@ss.hasPerm('cnt:execute')")
    @Idempotent
    @AuditLog(module = "cnt", action = "会诊完成", bizType = "cnt_request")
    public R<Void> complete(@PathVariable Long id, @RequestParam String opinion) {
        // 一百零七轮 C7：空意见网关拦截（服务层兜底双保险）
        if (opinion == null || opinion.isBlank()) {
            throw new com.his.common.BizException(com.his.common.ErrorCode.A0001, "会诊意见不能为空");
        }
        cntService.complete(id, opinion.trim());
        return R.ok();
    }
}
