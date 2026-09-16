package com.his.modules.emr.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.emr.dto.EmrQcRequest;
import com.his.modules.emr.dto.EmrRecordCreateRequest;
import com.his.modules.emr.dto.EmrRecordQuery;
import com.his.modules.emr.dto.EmrRecordUpdateRequest;
import com.his.modules.emr.dto.EmrTemplateRequest;
import com.his.modules.emr.entity.EmrRecord;
import com.his.modules.emr.entity.EmrTemplate;
import com.his.modules.emr.service.EmrService;
import jakarta.validation.Valid;
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

/**
 * EMR 接口（E-01~E-08）。
 */
@RestController
@RequestMapping("/api/v1/emr")
@RequiredArgsConstructor
public class EmrController {
    private final EmrService emrService;

    @PostMapping("/records")
    @PreAuthorize("@ss.hasPerm('emr:record:create')")
    @Idempotent
    @AuditLog(module = "emr", action = "新建文书", bizType = "emr_record")
    public R<java.util.Map<String, Object>> create(@Valid @RequestBody EmrRecordCreateRequest req) {
        return R.ok(emrService.create(req));
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("@ss.hasPerm('emr:record:update')")
    @Idempotent
    @AuditLog(module = "emr", action = "暂存文书", bizType = "emr_record")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody EmrRecordUpdateRequest req) {
        emrService.update(id, req);
        return R.ok();
    }

    @PostMapping("/records/{id}/submit")
    @PreAuthorize("@ss.hasPerm('emr:record:submit')")
    @Idempotent
    @AuditLog(module = "emr", action = "提交文书", bizType = "emr_record")
    public R<Void> submit(@PathVariable Long id) {
        emrService.submit(id);
        return R.ok();
    }

    @GetMapping("/records")
    @PreAuthorize("@ss.hasPerm('emr:record:query')")
    public R<PageResult<EmrRecord>> page(EmrRecordQuery query) {
        return R.ok(emrService.page(query));
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("@ss.hasPerm('emr:record:query')")
    public R<EmrRecord> detail(@PathVariable Long id) {
        return R.ok(emrService.detail(id));
    }

    @PostMapping("/records/{id}/qc")
    @PreAuthorize("@ss.hasPerm('emr:qc:do')")
    @Idempotent
    @AuditLog(module = "emr", action = "文书质控", bizType = "emr_record")
    public R<Void> qc(@PathVariable Long id, @Valid @RequestBody EmrQcRequest req) {
        emrService.qc(id, req);
        return R.ok();
    }

    @GetMapping("/qc/pending")
    @PreAuthorize("@ss.hasPerm('emr:qc:do')")
    public R<List<EmrRecord>> qcPending() {
        return R.ok(emrService.qcPending());
    }

    @GetMapping("/templates")
    @PreAuthorize("@ss.hasPerm('emr:record:query')")
    public R<List<EmrTemplate>> templates(@RequestParam(required = false) Integer docType) {
        return R.ok(emrService.templates(docType));
    }

    @PostMapping("/templates")
    @PreAuthorize("@ss.hasPerm('emr:template:manage')")
    @Idempotent
    @AuditLog(module = "emr", action = "新建模板", bizType = "emr_template")
    public R<Long> createTemplate(@Valid @RequestBody EmrTemplateRequest req) {
        return R.ok(emrService.createTemplate(req));
    }

    @PutMapping("/templates/{id}/stop")
    @PreAuthorize("@ss.hasPerm('emr:template:manage')")
    @Idempotent
    @AuditLog(module = "emr", action = "停用模板", bizType = "emr_template")
    public R<Void> stopTemplate(@PathVariable Long id) {
        emrService.stopTemplate(id);
        return R.ok();
    }
}
