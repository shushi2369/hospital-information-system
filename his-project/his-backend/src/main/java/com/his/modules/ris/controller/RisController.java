package com.his.modules.ris.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.ris.dto.*;
import com.his.modules.ris.entity.RisDevice;
import com.his.modules.ris.entity.RisReport;
import com.his.modules.ris.entity.RisRequest;
import com.his.modules.ris.service.RisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RIS 影像接口（R-01~R-10，《16》§5.2）。检查申请由护士执行医嘱自动生成，无独立创建接口。
 */
@RestController
@RequestMapping("/api/v1/ris")
@RequiredArgsConstructor
public class RisController {
    private final RisService risService;

    @GetMapping("/requests")
    @PreAuthorize("@ss.hasPerm('ris:request:query')")
    public R<PageResult<RisRequest>> page(RisRequestQuery query) {
        return R.ok(risService.page(query));
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("@ss.hasPerm('ris:request:query')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(risService.detail(id));
    }

    @PostMapping("/requests/{id}/appoint")
    @PreAuthorize("@ss.hasPerm('ris:appt:manage')")
    @Idempotent
    @AuditLog(module = "ris", action = "检查预约", bizType = "ris_appointment")
    public R<Long> appoint(@PathVariable Long id, @Valid @RequestBody AppointRequest req) {
        return R.ok(risService.appoint(id, req));
    }

    @PostMapping("/requests/{id}/start")
    @PreAuthorize("@ss.hasPerm('ris:exam:execute')")
    @Idempotent
    @AuditLog(module = "ris", action = "开始检查", bizType = "ris_request")
    public R<Void> start(@PathVariable Long id) {
        risService.start(id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/images")
    @PreAuthorize("@ss.hasPerm('ris:exam:execute')")
    @Idempotent
    @AuditLog(module = "ris", action = "影像归档", bizType = "ris_image")
    public R<String> archive(@PathVariable Long id, @Valid @RequestBody ImageArchiveRequest req) {
        return R.ok(risService.archive(id, req));
    }

    @PostMapping("/requests/{id}/finish")
    @PreAuthorize("@ss.hasPerm('ris:exam:execute')")
    @Idempotent
    @AuditLog(module = "ris", action = "检查完成", bizType = "ris_request")
    public R<Void> finish(@PathVariable Long id) {
        risService.finish(id);
        return R.ok();
    }

    @PostMapping("/reports")
    @PreAuthorize("@ss.hasPerm('ris:report:write')")
    @Idempotent
    @AuditLog(module = "ris", action = "书写报告", bizType = "ris_report")
    public R<String> writeReport(@Valid @RequestBody ReportWriteRequest req) {
        return R.ok(risService.writeReport(req));
    }

    @PostMapping("/reports/{id}/review")
    @PreAuthorize("@ss.hasPerm('ris:report:review')")
    @Idempotent
    @AuditLog(module = "ris", action = "报告审核发布", bizType = "ris_report")
    public R<Void> reviewReport(@PathVariable Long id, @Valid @RequestBody ReportReviewRequest req) {
        risService.reviewReport(id, req);
        return R.ok();
    }

    @GetMapping("/reports")
    @PreAuthorize("@ss.hasPerm('ris:report:query')")
    public R<PageResult<RisReport>> reportPage(RisRequestQuery query) {
        return R.ok(risService.reportPage(query));
    }

    @GetMapping("/reports/{requestId}")
    @PreAuthorize("@ss.hasPerm('ris:report:query')")
    public R<Map<String, Object>> reportDetail(@PathVariable Long requestId) {
        return R.ok(risService.reportDetail(requestId));
    }

    @GetMapping("/devices")
    @PreAuthorize("@ss.hasPerm('ris:device:query')")
    public R<List<RisDevice>> devices() {
        return R.ok(risService.devices());
    }
}
