package com.his.modules.lis.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.lis.dto.LisRequestQuery;
import com.his.modules.lis.dto.ResultEntryRequest;
import com.his.modules.lis.dto.ThresholdRequest;
import com.his.modules.lis.entity.LisCriticalThreshold;
import com.his.modules.lis.entity.LisReport;
import com.his.modules.lis.entity.LisRequest;
import com.his.modules.lis.service.LisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * LIS 检验接口（L-01~L-10）。
 */
@RestController
@RequestMapping("/api/v1/lis")
@RequiredArgsConstructor
public class LisController {
    private final LisService lisService;

    @GetMapping("/requests")
    @PreAuthorize("@ss.hasPerm('lab:request:query')")
    public R<PageResult<LisRequest>> page(LisRequestQuery query) {
        return R.ok(lisService.page(query));
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("@ss.hasPerm('lab:request:query')")
    public R<LisRequest> detail(@PathVariable Long id) {
        return R.ok(lisService.requireRequest(id));
    }

    @PostMapping("/specimens/collect")
    @PreAuthorize("@ss.hasPerm('lab:specimen:collect')")
    @Idempotent
    @AuditLog(module = "lis", action = "标本采集", bizType = "lis_request")
    public R<Map<String, Object>> collect(@RequestParam Long requestId) {
        return R.ok(lisService.collect(requestId));
    }

    @PostMapping("/specimens/{requestId}/receive")
    @PreAuthorize("@ss.hasPerm('lab:specimen:collect')")
    @Idempotent
    @AuditLog(module = "lis", action = "标本接收", bizType = "lis_request")
    public R<Void> receive(@PathVariable Long requestId) {
        lisService.receive(requestId);
        return R.ok();
    }

    @PostMapping("/results/entry")
    @PreAuthorize("@ss.hasPerm('lab:result:entry')")
    @Idempotent
    @AuditLog(module = "lis", action = "结果录入", bizType = "lis_result")
    public R<Integer> entry(@Valid @RequestBody ResultEntryRequest req) {
        return R.ok(lisService.entry(req));
    }

    @PostMapping("/reports/{requestId}/publish")
    @PreAuthorize("@ss.hasPerm('lab:report:publish')")
    @Idempotent
    @AuditLog(module = "lis", action = "报告发布", bizType = "lis_report")
    public R<String> publish(@PathVariable Long requestId,
            @RequestParam(required = false) @Min(0) @Max(1) Integer mutualFlag,
            @RequestParam(required = false) String mutualNote) {
        return R.ok(lisService.publish(requestId, mutualFlag, mutualNote));
    }

    @GetMapping("/reports")
    @PreAuthorize("@ss.hasPerm('lab:report:query')")
    public R<PageResult<LisReport>> reports(LisRequestQuery query) {
        return R.ok(lisService.reportPage(query));
    }

    @GetMapping("/reports/{requestId}")
    @PreAuthorize("@ss.hasPerm('lab:report:query')")
    public R<Map<String, Object>> reportDetail(@PathVariable Long requestId) {
        return R.ok(lisService.reportDetail(requestId));
    }

    @GetMapping("/thresholds")
    @PreAuthorize("@ss.hasPerm('lab:threshold:query')")
    public R<List<LisCriticalThreshold>> thresholds() {
        return R.ok(lisService.thresholds());
    }

    @PostMapping("/thresholds")
    @PreAuthorize("@ss.hasPerm('lab:threshold:manage')")
    @Idempotent
    @AuditLog(module = "lis", action = "阈值维护", bizType = "lis_critical_threshold")
    public R<Long> saveThreshold(@Valid @RequestBody ThresholdRequest req) {
        return R.ok(lisService.saveThreshold(req));
    }
}
