package com.his.modules.emc.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.emc.dto.*;
import com.his.modules.emc.entity.EmcTriage;
import com.his.modules.emc.entity.EmcVisit;
import com.his.modules.emc.service.EmcService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * EMC 急诊五大中心接口（E-01~E-09，《16》§5.3）。
 */
@RestController
@RequestMapping("/api/v1/emc")
@RequiredArgsConstructor
public class EmcController {
    private final EmcService emcService;

    @PostMapping("/triage")
    @PreAuthorize("@ss.hasPerm('emc:triage:create')")
    @Idempotent
    @AuditLog(module = "emc", action = "急诊分诊登记", bizType = "emc_triage")
    public R<String> triage(@Valid @RequestBody TriageRequest req) {
        return R.ok(emcService.triage(req));
    }

    @GetMapping("/triage")
    @PreAuthorize("@ss.hasPerm('emc:triage:query')")
    public R<PageResult<EmcTriage>> pageTriage(EmcVisitQuery query) {
        return R.ok(emcService.pageTriage(query));
    }

    @PostMapping("/visits")
    @PreAuthorize("@ss.hasPerm('emc:visit:create')")
    @Idempotent
    @AuditLog(module = "emc", action = "五大中心登记", bizType = "emc_visit")
    public R<String> createVisit(@Valid @RequestBody VisitCreateRequest req) {
        return R.ok(emcService.createVisit(req));
    }

    @GetMapping("/visits")
    @PreAuthorize("@ss.hasPerm('emc:visit:query')")
    public R<PageResult<EmcVisit>> pageVisits(EmcVisitQuery query) {
        return R.ok(emcService.pageVisits(query));
    }

    @GetMapping("/visits/{id}")
    @PreAuthorize("@ss.hasPerm('emc:visit:query')")
    public R<Map<String, Object>> visitDetail(@PathVariable Long id) {
        return R.ok(emcService.visitDetail(id));
    }

    @PostMapping("/visits/{id}/timepoints")
    @PreAuthorize("@ss.hasPerm('emc:timepoint:record')")
    @Idempotent
    @AuditLog(module = "emc", action = "时间节点录入", bizType = "emc_timepoint")
    public R<Long> addTimepoint(@PathVariable Long id, @Valid @RequestBody TimepointRequest req) {
        return R.ok(emcService.addTimepoint(id, req));
    }

    @PostMapping("/visits/{id}/link")
    @PreAuthorize("@ss.hasPerm('emc:visit:create')")
    @Idempotent
    @AuditLog(module = "emc", action = "后补关联就诊", bizType = "emc_visit")
    public R<Void> link(@PathVariable Long id, @Valid @RequestBody LinkRequest req) {
        emcService.link(id, req);
        return R.ok();
    }

    @PostMapping("/visits/{id}/close")
    @PreAuthorize("@ss.hasPerm('emc:visit:close')")
    @Idempotent
    @AuditLog(module = "emc", action = "病例关档", bizType = "emc_visit")
    public R<Void> close(@PathVariable Long id, @Valid @RequestBody CloseRequest req) {
        emcService.close(id, req);
        return R.ok();
    }

    @GetMapping("/visits/{id}/timeline")
    @PreAuthorize("@ss.hasPerm('emc:visit:query')")
    public R<List<Map<String, Object>>> timeline(@PathVariable Long id) {
        return R.ok(emcService.timeline(id));
    }

    @GetMapping("/nodes")
    @PreAuthorize("@ss.hasPerm('emc:visit:query')")
    public R<List<com.his.modules.emc.entity.EmcNodeDict>> nodes() {
        return R.ok(emcService.listNodes());
    }

    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPerm('emc:stats:query')")
    public R<List<Map<String, Object>>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return R.ok(emcService.stats(from, to));
    }
}
