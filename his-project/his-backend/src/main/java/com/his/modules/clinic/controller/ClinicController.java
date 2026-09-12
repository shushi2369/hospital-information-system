package com.his.modules.clinic.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.PageResult;
import com.his.common.R;
import com.his.modules.clinic.dto.DiagnosisCreateRequest;
import com.his.modules.clinic.dto.ClinicQueueItemDTO;
import com.his.modules.clinic.dto.ExamApplicationCreateRequest;
import com.his.modules.clinic.dto.ExamCreateResult;
import com.his.modules.clinic.dto.OrderCreateRequest;
import com.his.modules.clinic.dto.PrescriptionCreateRequest;
import com.his.modules.clinic.dto.PrescriptionCreateResult;
import com.his.modules.clinic.dto.PrescriptionVoidRequest;
import com.his.modules.clinic.dto.RecordUpdateRequest;
import com.his.modules.clinic.dto.VisitBrief;
import com.his.modules.clinic.dto.VisitDetailResponse;
import com.his.modules.clinic.dto.VisitPageQuery;
import com.his.modules.clinic.service.ClinicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 医生工作站接口（C-01~C-12）。
 */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
public class ClinicController {
    private final ClinicService clinicService;

    @GetMapping("/queue")
    @PreAuthorize("@ss.hasPerm('clinic:visit:query')")
    public R<List<ClinicQueueItemDTO>> queue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(clinicService.myQueue(date == null ? LocalDate.now() : date));
    }

    @PostMapping("/visits/{registrationId}/start")
    @PreAuthorize("@ss.hasPerm('clinic:visit:do')")
    @Idempotent
    @AuditLog(module = "clinic", action = "接诊", bizType = "cli_visit")
    public R<Long> start(@PathVariable Long registrationId) {
        return R.ok(clinicService.start(registrationId));
    }

    @PutMapping("/visits/{id}/record")
    @PreAuthorize("@ss.hasPerm('clinic:record:update')")
    @Idempotent
    @AuditLog(module = "clinic", action = "暂存病历", bizType = "cli_visit")
    public R<Void> saveRecord(@PathVariable Long id, @Valid @RequestBody RecordUpdateRequest req) {
        clinicService.saveRecord(id, req);
        return R.ok();
    }

    @PostMapping("/visits/{id}/diagnoses")
    @PreAuthorize("@ss.hasPerm('clinic:diagnosis:create')")
    @Idempotent
    @AuditLog(module = "clinic", action = "新增诊断", bizType = "cli_diagnosis")
    public R<Long> addDiagnosis(@PathVariable Long id, @Valid @RequestBody DiagnosisCreateRequest req) {
        return R.ok(clinicService.addDiagnosis(id, req));
    }

    @DeleteMapping("/diagnoses/{id}")
    @PreAuthorize("@ss.hasPerm('clinic:diagnosis:create')")
    @Idempotent
    @AuditLog(module = "clinic", action = "删除诊断", bizType = "cli_diagnosis")
    public R<Void> deleteDiagnosis(@PathVariable Long id) {
        clinicService.deleteDiagnosis(id);
        return R.ok();
    }

    @PostMapping("/visits/{id}/orders")
    @PreAuthorize("@ss.hasPerm('clinic:record:update')")
    @Idempotent
    @AuditLog(module = "clinic", action = "新增医嘱", bizType = "cli_medical_order")
    public R<Long> addOrder(@PathVariable Long id, @Valid @RequestBody OrderCreateRequest req) {
        return R.ok(clinicService.addOrder(id, req));
    }

    @PostMapping("/visits/{id}/prescriptions")
    @PreAuthorize("@ss.hasPerm('clinic:prescription:create')")
    @Idempotent
    @AuditLog(module = "clinic", action = "开处方", bizType = "cli_prescription")
    public R<PrescriptionCreateResult> createPrescription(@PathVariable Long id,
                                                          @Valid @RequestBody PrescriptionCreateRequest req) {
        return R.ok(clinicService.createPrescription(id, req));
    }

    @PostMapping("/prescriptions/{id}/void")
    @PreAuthorize("@ss.hasPerm('clinic:prescription:create')")
    @Idempotent
    @AuditLog(module = "clinic", action = "作废处方", bizType = "cli_prescription")
    public R<Void> voidPrescription(@PathVariable Long id,
                                    @RequestBody(required = false) PrescriptionVoidRequest req) {
        clinicService.voidPrescription(id, req);
        return R.ok();
    }

    @PostMapping("/visits/{id}/exam-applications")
    @PreAuthorize("@ss.hasPerm('clinic:exam:create')")
    @Idempotent
    @AuditLog(module = "clinic", action = "检查检验申请", bizType = "cli_exam_application")
    public R<ExamCreateResult> createExamApplication(@PathVariable Long id,
                                                     @Valid @RequestBody ExamApplicationCreateRequest req) {
        return R.ok(clinicService.createExamApplication(id, req));
    }

    @PostMapping("/visits/{id}/complete")
    @PreAuthorize("@ss.hasPerm('clinic:record:submit')")
    @Idempotent
    @AuditLog(module = "clinic", action = "提交病历", bizType = "cli_visit")
    public R<Void> complete(@PathVariable Long id) {
        clinicService.complete(id);
        return R.ok();
    }

    @GetMapping("/visits/{id}")
    @PreAuthorize("@ss.hasPerm('clinic:visit:query')")
    public R<VisitDetailResponse> detail(@PathVariable Long id) {
        return R.ok(clinicService.detail(id));
    }

    @GetMapping("/visits")
    @PreAuthorize("@ss.hasPerm('clinic:visit:query')")
    public R<PageResult<VisitBrief>> page(VisitPageQuery query) {
        return R.ok(clinicService.page(query));
    }
}
