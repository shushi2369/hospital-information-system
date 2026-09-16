package com.his.modules.mrc.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.mrc.dto.BorrowRequest;
import com.his.modules.mrc.dto.HomepageCodeRequest;
import com.his.modules.mrc.dto.HomepageQcRequest;
import com.his.modules.mrc.dto.MrcQuery;
import com.his.modules.mrc.dto.MrcRecordVO;
import com.his.modules.mrc.entity.MrcHomepage;
import com.his.modules.mrc.entity.MrcIcd10;
import com.his.modules.mrc.entity.MrcRecord;
import com.his.modules.mrc.service.MrcService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 病案管理接口（M-01~M-08）。
 */
@RestController
@RequestMapping("/api/v1/mrc")
@RequiredArgsConstructor
public class MrcController {
    private final MrcService mrcService;

    @GetMapping("/records")
    @PreAuthorize("@ss.hasPerm('mrc:archive:query')")
    public R<PageResult<MrcRecordVO>> page(MrcQuery query) {
        return R.ok(mrcService.page(query));
    }

    @PostMapping("/admissions/{admissionId}/archive")
    @PreAuthorize("@ss.hasPerm('mrc:archive:do')")
    @Idempotent
    @AuditLog(module = "mrc", action = "病案归档", bizType = "mrc_record")
    public R<Void> archive(@PathVariable Long admissionId) {
        mrcService.archive(admissionId);
        return R.ok();
    }

    @PostMapping("/homepage/{admissionId}/code")
    @PreAuthorize("@ss.hasPerm('mrc:homepage:code')")
    @Idempotent
    @AuditLog(module = "mrc", action = "首页编码", bizType = "mrc_homepage")
    public R<Void> code(@PathVariable Long admissionId, @Valid @RequestBody HomepageCodeRequest req) {
        mrcService.code(admissionId, req);
        return R.ok();
    }

    @PostMapping("/homepage/{admissionId}/qc")
    @PreAuthorize("@ss.hasPerm('mrc:homepage:qc')")
    @Idempotent
    @AuditLog(module = "mrc", action = "首页质控", bizType = "mrc_homepage")
    public R<Void> qc(@PathVariable Long admissionId, @Valid @RequestBody HomepageQcRequest req) {
        mrcService.qc(admissionId, req);
        return R.ok();
    }

    @GetMapping("/homepage/{admissionId}")
    @PreAuthorize("@ss.hasPerm('mrc:archive:query')")
    public R<MrcHomepage> homepage(@PathVariable Long admissionId) {
        return R.ok(mrcService.homepage(admissionId));
    }

    @PostMapping("/admissions/{admissionId}/borrow")
    @PreAuthorize("@ss.hasPerm('mrc:borrow:create')")
    @Idempotent
    @AuditLog(module = "mrc", action = "病案借阅", bizType = "mrc_borrow")
    public R<Long> borrow(@PathVariable Long admissionId, @Valid @RequestBody BorrowRequest req) {
        return R.ok(mrcService.borrow(admissionId, req));
    }

    @PostMapping("/admissions/{admissionId}/return")
    @PreAuthorize("@ss.hasPerm('mrc:borrow:create')")
    @Idempotent
    @AuditLog(module = "mrc", action = "病案归还", bizType = "mrc_borrow")
    public R<Void> giveBack(@PathVariable Long admissionId) {
        mrcService.giveBack(admissionId);
        return R.ok();
    }

    @GetMapping("/icd10")
    @PreAuthorize("@ss.hasPerm('mrc:archive:query')")
    public R<List<MrcIcd10>> icd10(@RequestParam(required = false) String keyword) {
        return R.ok(mrcService.icd10(keyword));
    }
}
