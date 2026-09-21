package com.his.modules.ae.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.ae.entity.AeEvent;
import com.his.modules.ae.service.AeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 不良事件接口（AE-01~05，四期三批）。 */
@RestController
@RequestMapping("/api/v1/ae")
@RequiredArgsConstructor
public class AeController {
    private final AeService aeService;

    @PostMapping
    @PreAuthorize("@ss.hasPerm('ae:report')")
    @Idempotent
    @AuditLog(module = "ae", action = "不良事件上报", bizType = "ae_event")
    public R<String> report(@RequestBody AeEvent event) {
        return R.ok(aeService.report(event));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPerm('ae:query')")
    public R<PageResult<AeEvent>> page(com.his.common.PageQuery query,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer eventType) {
        return R.ok(aeService.page(query, status, eventType));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("@ss.hasPerm('ae:qc:assign')")
    @Idempotent
    @AuditLog(module = "ae", action = "质控分派", bizType = "ae_event")
    public R<Void> assign(@PathVariable Long id) {
        aeService.assign(id);
        return R.ok();
    }

    @PostMapping("/{id}/rectify")
    @PreAuthorize("@ss.hasPerm('ae:report')")
    @Idempotent
    @AuditLog(module = "ae", action = "整改提交", bizType = "ae_event")
    public R<Void> rectify(@PathVariable Long id, @RequestParam String handlerNote) {
        aeService.rectify(id, handlerNote);
        return R.ok();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.hasPerm('ae:close')")
    @Idempotent
    @AuditLog(module = "ae", action = "不良事件闭环", bizType = "ae_event")
    public R<Void> close(@PathVariable Long id) {
        aeService.close(id);
        return R.ok();
    }
}
