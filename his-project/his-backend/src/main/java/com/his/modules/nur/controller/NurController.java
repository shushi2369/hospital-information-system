package com.his.modules.nur.controller;

import com.his.common.AuditLog;
import com.his.common.Idempotent;
import com.his.common.R;
import com.his.modules.doc.entity.DocOrderExec;
import com.his.modules.doc.service.DocOrderService;
import com.his.modules.inp.dto.BedVO;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.service.InpService;
import com.his.modules.nur.dto.ScheduleRequest;
import com.his.modules.nur.dto.VitalSignRequest;
import com.his.modules.nur.entity.NurSchedule;
import com.his.modules.nur.entity.NurVitalSign;
import com.his.modules.nur.service.NurService;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 护理工作台接口（N-01~N-06）。
 */
@RestController
@RequestMapping("/api/v1/nur")
@RequiredArgsConstructor
public class NurController {
    private final NurService nurService;
    private final DocOrderService docOrderService;
    private final InpService inpService;
    private final InpAppService inpAppService;
    private final PatientAppService patientAppService;

    @PostMapping("/schedules")
    @PreAuthorize("@ss.hasPerm('nur:schedule:manage')")
    @Idempotent
    @AuditLog(module = "nur", action = "护理排班", bizType = "nur_schedule")
    public R<Long> schedule(@Valid @RequestBody ScheduleRequest req) {
        return R.ok(nurService.schedule(req));
    }

    @GetMapping("/schedules")
    @PreAuthorize("@ss.hasPerm('nur:schedule:manage')")
    public R<List<NurSchedule>> schedules(
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shiftDate) {
        return R.ok(nurService.schedules(wardId, shiftDate));
    }

    @PostMapping("/vital-signs")
    @PreAuthorize("@ss.hasPerm('nur:vital:create')")
    @Idempotent
    @AuditLog(module = "nur", action = "体征录入", bizType = "nur_vital_sign")
    public R<Long> addVitalSign(@Valid @RequestBody VitalSignRequest req) {
        return R.ok(nurService.addVitalSign(req));
    }

    @GetMapping("/vital-signs")
    @PreAuthorize("@ss.hasPerm('nur:vital:query')")
    public R<List<NurVitalSign>> vitalSigns(
            @RequestParam Long admissionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(nurService.vitalSigns(admissionId, startTime, endTime));
    }

    /** 在院患者列表（N-04，PDA 首页）：本护理病区 */
    @GetMapping("/patients")
    @PreAuthorize("@ss.hasPerm('nur:exec:do')")
    public R<List<Map<String, Object>>> patients(@RequestParam(required = false) Long wardId) {
        List<InpAdmission> admissions = inpAppService.listInHospitalByWard(wardId);
        List<Long> patientIds = admissions.stream().map(InpAdmission::getPatientId).distinct().toList();
        Map<Long, PatientDTO> patients = new HashMap<>();
        for (PatientDTO p : patientAppService.listByIds(patientIds)) {
            patients.put(p.getId(), p);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (InpAdmission admission : admissions) {
            Map<String, Object> row = new HashMap<>();
            row.put("admissionId", admission.getId());
            row.put("admissionNo", admission.getAdmissionNo());
            row.put("patientId", admission.getPatientId());
            PatientDTO p = patients.get(admission.getPatientId());
            row.put("patientName", p == null ? null : p.getName());
            row.put("bedId", admission.getBedId());
            row.put("status", admission.getStatus());
            result.add(row);
        }
        return R.ok(result);
    }

    /** 待执行/待核对汇总（N-05）：当日执行单 */
    @GetMapping("/executions/pending")
    @PreAuthorize("@ss.hasPerm('nur:exec:do')")
    public R<List<DocOrderExec>> pendingExecutions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate execDate) {
        return R.ok(docOrderService.todo(execDate == null ? LocalDate.now() : execDate));
    }

    /** 病区床位一览（N-06） */
    @GetMapping("/wards/beds")
    @PreAuthorize("@ss.hasPerm('nur:exec:do')")
    public R<List<BedVO>> beds(
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) Integer bedStatus) {
        return R.ok(inpService.beds(wardId, bedStatus));
    }
}
