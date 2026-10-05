package com.his.modules.rpt.controller;

import com.his.common.AuditLog;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.rpt.entity.RptUpload;
import com.his.modules.rpt.service.RptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 区域平台上报管理（五期-lite）：队列查询 / 详情 / 手动重报 / 统计。 */
@RestController
@RequestMapping("/api/v1/rpt/uploads")
public class RptController {

    private final RptService rptService;

    public RptController(RptService rptService) {
        this.rptService = rptService;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPerm('rpt:upload:query')")
    public R<PageResult<RptUpload>> page(@RequestParam(required = false) Integer bizType,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(rptService.page(bizType, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('rpt:upload:query')")
    public R<RptUpload> detail(@PathVariable Long id) {
        return R.ok(rptService.detail(id));
    }

    /** 手动重报：失败单回队并立即投递一次（PHI 出域动作，必须留痕） */
    @PostMapping("/{id}/retry")
    @PreAuthorize("@ss.hasPerm('rpt:upload:retry')")
    @AuditLog(module = "rpt", action = "手动重报上报单")
    public R<RptUpload> retry(@PathVariable Long id) {
        return R.ok(rptService.retryNow(id));
    }

    /** 手动触发一批投递（管理端/e2e 用） */
    @PostMapping("/deliver")
    @PreAuthorize("@ss.hasPerm('rpt:upload:retry')")
    @AuditLog(module = "rpt", action = "手动批量投递上报队列")
    public R<Integer> deliver(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(rptService.deliverBatch(limit));
    }

    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPerm('rpt:upload:query')")
    public R<Map<String, Object>> stats() {
        return R.ok(rptService.stats());
    }
}
