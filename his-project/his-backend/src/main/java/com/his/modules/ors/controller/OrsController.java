package com.his.modules.ors.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.ors.dto.*;
import com.his.modules.ors.entity.OrsOperateRoom;
import com.his.modules.ors.entity.OrsSurgeryRequest;
import com.his.modules.ors.service.OrsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ORIS 手术麻醉接口（OR-01~OR-13，《16》§5.1）。
 */
@RestController
@RequestMapping("/api/v1/ors")
@RequiredArgsConstructor
public class OrsController {
    private final OrsService orsService;

    @PostMapping("/requests")
    @PreAuthorize("@ss.hasPerm('or:request:create')")
    @Idempotent
    @AuditLog(module = "ors", action = "创建手术申请", bizType = "or_surgery_request")
    public R<String> create(@Valid @RequestBody SurgeryCreateRequest req) {
        return R.ok(orsService.create(req));
    }

    @GetMapping("/requests")
    @PreAuthorize("@ss.hasPerm('or:request:query')")
    public R<PageResult<OrsSurgeryRequest>> page(OrsRequestQuery query) {
        return R.ok(orsService.page(query));
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("@ss.hasPerm('or:request:query')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(orsService.detail(id));
    }

    @PostMapping("/requests/{id}/review")
    @PreAuthorize("@ss.hasPerm('or:request:review')")
    @Idempotent
    @AuditLog(module = "ors", action = "手术审核", bizType = "or_surgery_request")
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        orsService.review(id, req);
        return R.ok();
    }

    @PostMapping("/requests/{id}/schedule")
    @PreAuthorize("@ss.hasPerm('or:schedule:manage')")
    @Idempotent
    @AuditLog(module = "ors", action = "手术排台", bizType = "or_schedule")
    public R<String> schedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest req) {
        return R.ok(orsService.schedule(id, req));
    }

    @PostMapping("/requests/{id}/checks")
    @PreAuthorize("@ss.hasPerm('or:check:submit')")
    @Idempotent
    @AuditLog(module = "ors", action = "提交安全核查单", bizType = "or_check_record")
    public R<Long> submitCheck(@PathVariable Long id, @Valid @RequestBody CheckSubmitRequest req) {
        return R.ok(orsService.submitCheck(id, req));
    }

    @PostMapping("/requests/{id}/start")
    @PreAuthorize("@ss.hasPerm('or:stage:operate')")
    @Idempotent
    @AuditLog(module = "ors", action = "开始手术", bizType = "or_surgery_request")
    public R<Void> start(@PathVariable Long id) {
        orsService.start(id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/anesthesia")
    @PreAuthorize("@ss.hasPerm('or:anesthesia:write')")
    @Idempotent
    @AuditLog(module = "ors", action = "保存麻醉记录", bizType = "or_anesthesia_record")
    public R<String> anesthesia(@PathVariable Long id, @Valid @RequestBody AnesthesiaRequest req) {
        return R.ok(orsService.saveAnesthesia(id, req));
    }

    @PostMapping("/requests/{id}/finish")
    @PreAuthorize("@ss.hasPerm('or:stage:operate')")
    @Idempotent
    @AuditLog(module = "ors", action = "手术结束", bizType = "or_surgery_request")
    public R<Void> finish(@PathVariable Long id) {
        orsService.finish(id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/leave")
    @PreAuthorize("@ss.hasPerm('or:stage:operate')")
    @Idempotent
    @AuditLog(module = "ors", action = "离室登记", bizType = "or_postop_record")
    public R<Void> leave(@PathVariable Long id, @Valid @RequestBody LeaveRequest req) {
        orsService.leave(id, req);
        return R.ok();
    }

    @PostMapping("/requests/{id}/complete")
    @PreAuthorize("@ss.hasPerm('or:request:complete')")
    @Idempotent
    @AuditLog(module = "ors", action = "完成关档记账", bizType = "or_surgery_request")
    public R<Map<String, Object>> complete(@PathVariable Long id) {
        return R.ok(orsService.complete(id));
    }

    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("@ss.hasPerm('or:request:create')")
    @Idempotent
    @AuditLog(module = "ors", action = "取消手术申请", bizType = "or_surgery_request")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        orsService.cancel(id, reason);
        return R.ok();
    }

    @GetMapping("/rooms")
    @PreAuthorize("@ss.hasPerm('or:room:query')")
    public R<List<OrsOperateRoom>> rooms() {
        return R.ok(orsService.rooms());
    }
}
