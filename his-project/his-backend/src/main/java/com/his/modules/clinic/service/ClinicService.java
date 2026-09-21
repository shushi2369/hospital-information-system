package com.his.modules.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.security.LoginUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.basedata.app.DrugDTO;
import com.his.modules.basedata.app.DepartmentDTO;
import com.his.modules.clinic.dto.ClinicQueueItemDTO;
import com.his.modules.clinic.dto.DiagnosisCreateRequest;
import com.his.modules.clinic.dto.ExamApplicationCreateRequest;
import com.his.modules.clinic.dto.ExamCreateResult;
import com.his.modules.clinic.dto.OrderCreateRequest;
import com.his.modules.clinic.dto.PrescriptionCreateRequest;
import com.his.modules.clinic.dto.PrescriptionCreateResult;
import com.his.modules.clinic.dto.PrescriptionItemRequest;
import com.his.modules.clinic.dto.PrescriptionVoidRequest;
import com.his.modules.clinic.dto.RecordUpdateRequest;
import com.his.modules.clinic.dto.VisitBrief;
import com.his.modules.clinic.dto.VisitDetailResponse;
import com.his.modules.clinic.dto.VisitPageQuery;
import com.his.modules.clinic.entity.CliDiagnosis;
import com.his.modules.clinic.entity.CliExamApplication;
import com.his.modules.clinic.entity.CliMedicalOrder;
import com.his.modules.clinic.entity.CliPrescription;
import com.his.modules.clinic.entity.CliPrescriptionItem;
import com.his.modules.clinic.entity.CliVisit;
import com.his.modules.clinic.mapper.CliDiagnosisMapper;
import com.his.modules.clinic.mapper.CliExamApplicationMapper;
import com.his.modules.clinic.mapper.CliMedicalOrderMapper;
import com.his.modules.clinic.mapper.CliPrescriptionItemMapper;
import com.his.modules.clinic.mapper.CliPrescriptionMapper;
import com.his.modules.clinic.mapper.CliVisitMapper;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.registration.app.RegistrationAppService;
import com.his.modules.registration.app.RegistrationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * 医生工作站服务（C-01~C-12，规则《05》R15/R16 与状态机 2.2/2.3）。
 * 数据权限：医生仅可操作本人接诊记录，管理员全量（《04》§4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicService {
    private final CliVisitMapper visitMapper;
    private final CliDiagnosisMapper diagnosisMapper;
    private final CliMedicalOrderMapper orderMapper;
    private final CliPrescriptionMapper prescriptionMapper;
    private final CliPrescriptionItemMapper prescriptionItemMapper;
    private final CliExamApplicationMapper examApplicationMapper;
    private final RegistrationAppService registrationAppService;
    private final PatientAppService patientAppService;
    private final BasedataAppService basedataAppService;
    private final IdGenerator idGenerator;

    /** 我的候诊列表（C-01）：当前登录医生当日 status=10 挂号单，附带已存在的就诊 ID */
    public List<ClinicQueueItemDTO> myQueue(LocalDate visitDate) {
        Long doctorId = currentDoctorId(null);
        List<RegistrationDTO> regs = registrationAppService.listQueueByDoctor(doctorId, visitDate);
        if (regs.isEmpty()) {
            return List.of();
        }
        // 批量取本医生名下这些挂号单对应的就诊记录（未接诊的挂号单无就诊）
        List<Long> regIds = regs.stream().map(RegistrationDTO::getId).toList();
        Map<Long, Long> visitIdByReg = visitMapper.selectList(new LambdaQueryWrapper<CliVisit>()
                        .in(CliVisit::getRegistrationId, regIds))
                .stream().collect(Collectors.toMap(CliVisit::getRegistrationId, CliVisit::getId));
        return regs.stream().map(reg -> {
            ClinicQueueItemDTO item = new ClinicQueueItemDTO();
            item.setId(reg.getId());
            item.setRegNo(reg.getRegNo());
            item.setPatientId(reg.getPatientId());
            item.setPatientName(reg.getPatientName());
            item.setPatientNo(reg.getPatientNo());
            item.setDeptId(reg.getDeptId());
            item.setDeptName(reg.getDeptName());
            item.setDoctorId(reg.getDoctorId());
            item.setDoctorName(reg.getDoctorName());
            item.setRegDate(reg.getRegDate());
            item.setPeriod(reg.getPeriod());
            item.setRegType(reg.getRegType());
            item.setQueueNo(reg.getQueueNo());
            item.setStatus(reg.getStatus());
            item.setChargeStatus(reg.getChargeStatus());
            item.setVisitId(visitIdByReg.get(reg.getId()));
            return item;
        }).toList();
    }

    /** 接诊（C-02）：挂号单 10→30，创建就诊记录；重复接诊返回已有 visitId（幂等） */
    @Transactional
    public Long start(Long registrationId) {
        CliVisit existing = visitMapper.selectByRegistrationId(registrationId);
        if (existing != null) {
            checkVisitScope(existing);
            return existing.getId();
        }
        RegistrationDTO reg = registrationAppService.getById(registrationId);
        if (reg == null) {
            throw new BizException(ErrorCode.B1005);
        }
        checkRegistrationScope(reg);
        RegistrationDTO marked = registrationAppService.markVisited(registrationId);

        CliVisit visit = new CliVisit();
        visit.setVisitNo(idGenerator.next("JZ"));
        visit.setRegistrationId(marked.getId());
        visit.setPatientId(marked.getPatientId());
        visit.setDoctorId(marked.getDoctorId());
        visit.setDeptId(marked.getDeptId());
        visit.setVisitDate(marked.getRegDate());
        visit.setStatus(20);
        visit.setStartTime(LocalDateTime.now());
        visitMapper.insert(visit);
        return visit.getId();
    }

    /** 病历暂存（C-03）：仅接诊中可改（《05》B2001/B2002） */
    @Transactional
    public void saveRecord(Long visitId, RecordUpdateRequest req) {
        CliVisit visit = requireVisitInProgress(visitId);
        visit.setChiefComplaint(req.getChiefComplaint());
        visit.setPresentIllness(req.getPresentIllness());
        visit.setPhysicalExam(req.getPhysicalExam());
        visit.setAdvice(req.getAdvice());
        visitMapper.updateById(visit);
    }

    /** 新增诊断（C-04）：首条诊断自动为主诊断 */
    @Transactional
    public Long addDiagnosis(Long visitId, DiagnosisCreateRequest req) {
        CliVisit visit = requireVisitInProgress(visitId);
        Long count = diagnosisMapper.selectCount(new LambdaQueryWrapper<CliDiagnosis>()
                .eq(CliDiagnosis::getVisitId, visitId)
                .eq(CliDiagnosis::getStatus, 1));
        CliDiagnosis diagnosis = new CliDiagnosis();
        diagnosis.setVisitId(visitId);
        diagnosis.setDiagnosisCode(req.getDiagnosisCode());
        diagnosis.setDiagnosisName(req.getDiagnosisName());
        diagnosis.setDiagnosisType(count == null || count == 0 ? 1
                : req.getDiagnosisType() == null ? 2 : req.getDiagnosisType());
        diagnosis.setStatus(1);
        diagnosisMapper.insert(diagnosis);
        return diagnosis.getId();
    }

    /** 删除诊断（C-05）：软删留痕 */
    @Transactional
    public void deleteDiagnosis(Long diagnosisId) {
        CliDiagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        if (diagnosis == null) {
            throw new BizException(ErrorCode.A0001, "诊断不存在");
        }
        requireVisitInProgress(diagnosis.getVisitId());
        diagnosis.setStatus(0);
        diagnosisMapper.updateById(diagnosis);
    }

    /** 新增文字医嘱（C-06） */
    @Transactional
    public Long addOrder(Long visitId, OrderCreateRequest req) {
        requireVisitInProgress(visitId);
        CliMedicalOrder order = new CliMedicalOrder();
        order.setVisitId(visitId);
        order.setOrderType(req.getOrderType());
        order.setContent(req.getContent());
        order.setStatus(1);
        orderMapper.insert(order);
        return order.getId();
    }

    /** 开处方（C-07）：后端按药品字典取价重算金额，生成待审核处方 */
    @Transactional
    public PrescriptionCreateResult createPrescription(Long visitId, PrescriptionCreateRequest req) {
        CliVisit visit = requireVisitInProgress(visitId);
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ErrorCode.B2005);
        }
        // 先取药快照并重算总额（total_amount 非空列，主表须带总额插入）
        record PreparedRow(DrugDTO drug, PrescriptionItemRequest req, BigDecimal amount) {
        }
        BigDecimal total = BigDecimal.ZERO;
        List<PreparedRow> rows = new java.util.ArrayList<>();
        for (PrescriptionItemRequest item : req.getItems()) {
            DrugDTO drug = basedataAppService.getDrug(item.getDrugId());
            if (drug == null || drug.getStatus() == 0) {
                throw new BizException(ErrorCode.A0001, "药品不存在或已停用");
            }
            BigDecimal amount = drug.getRetailPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            rows.add(new PreparedRow(drug, item, amount));
        }
        CliPrescription prescription = new CliPrescription();
        prescription.setRxNo(idGenerator.next("CF"));
        prescription.setVisitId(visitId);
        prescription.setPatientId(visit.getPatientId());
        prescription.setDoctorId(visit.getDoctorId());
        prescription.setDeptId(visit.getDeptId());
        prescription.setRxType(1);
        prescription.setTotalAmount(total);
        prescription.setChargeStatus(0);
        prescription.setStatus(10);
        prescriptionMapper.insert(prescription);
        for (PreparedRow prepared : rows) {
            DrugDTO drug = prepared.drug();
            PrescriptionItemRequest item = prepared.req();
            CliPrescriptionItem row = new CliPrescriptionItem();
            row.setPrescriptionId(prescription.getId());
            row.setDrugId(drug.getId());
            row.setDrugName(drug.getDrugName());
            row.setSpec(drug.getSpec());
            row.setDosage(item.getDosage());
            row.setFrequency(item.getFrequency());
            row.setUsageRoute(item.getUsageRoute());
            row.setDays(item.getDays());
            row.setQuantity(item.getQuantity());
            row.setUnit(drug.getUnit());
            row.setUnitPrice(drug.getRetailPrice());
            row.setAmount(prepared.amount());
            row.setUsageNote(item.getUsageNote());
            row.setStatus(1);
            prescriptionItemMapper.insert(row);
        }
        return new PrescriptionCreateResult(prescription.getRxNo(), total);
    }

    /** 作废处方（C-08，《05》R16：仅未审核或审核通过未收费未发药可作废） */
    @Transactional
    public void voidPrescription(Long prescriptionId, PrescriptionVoidRequest req) {
        CliPrescription prescription = prescriptionMapper.selectById(prescriptionId);
        if (prescription == null) {
            throw new BizException(ErrorCode.A0001, "处方不存在");
        }
        checkVisitScope(requireVisit(prescription.getVisitId()));
        boolean voidable = prescription.getStatus() == 10
                || (prescription.getStatus() == 20 && prescription.getChargeStatus() == 0);
        if (!voidable) {
            throw new BizException(ErrorCode.B2006);
        }
        prescription.setStatus(50);
        prescription.setVoidReason(req == null ? null : req.getReason());
        prescriptionMapper.updateById(prescription);
    }

    /** 检查/检验申请（C-09）：项目类别 3检查费→1检查，4检验费→2检验 */
    @Transactional
    /** 门诊检查/检验申请列表（四期二批：回写状态验证） */
    public List<com.his.modules.clinic.entity.CliExamApplication> listExamApplications(Long visitId) {
        return examApplicationMapper.selectList(new LambdaQueryWrapper<com.his.modules.clinic.entity.CliExamApplication>()
                .eq(com.his.modules.clinic.entity.CliExamApplication::getVisitId, visitId)
                .orderByAsc(com.his.modules.clinic.entity.CliExamApplication::getId));
    }

    public ExamCreateResult createExamApplication(Long visitId, ExamApplicationCreateRequest req) {
        CliVisit visit = requireVisitInProgress(visitId);
        ChargeItemDTO item = basedataAppService.getChargeItem(req.getChargeItemId());
        if (item == null || item.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "收费项目不存在或已停用");
        }
        if (item.getCategory() != 3 && item.getCategory() != 4) {
            throw new BizException(ErrorCode.A0001, "只能选择检查/检验类收费项目");
        }
        CliExamApplication application = new CliExamApplication();
        application.setApplyNo(idGenerator.next("SQ"));
        application.setVisitId(visitId);
        application.setPatientId(visit.getPatientId());
        application.setDoctorId(visit.getDoctorId());
        application.setChargeItemId(item.getId());
        application.setApplyType(item.getCategory() == 3 ? 1 : 2);
        application.setRequirement(req.getRequirement());
        application.setPrice(item.getPrice());
        application.setChargeStatus(0);
        application.setStatus(10);
        examApplicationMapper.insert(application);
        return new ExamCreateResult(application.getApplyNo(), application.getPrice());
    }

    /** 提交病历（C-10）：主诉/现病史/诊断齐全（《05》B2003），完成后全部只读（R15） */
    @Transactional
    public void complete(Long visitId) {
        CliVisit visit = requireVisitInProgress(visitId);
        if (isBlank(visit.getChiefComplaint()) || isBlank(visit.getPresentIllness())) {
            throw new BizException(ErrorCode.B2003);
        }
        Long diagnosisCount = diagnosisMapper.selectCount(new LambdaQueryWrapper<CliDiagnosis>()
                .eq(CliDiagnosis::getVisitId, visitId)
                .eq(CliDiagnosis::getStatus, 1));
        if (diagnosisCount == null || diagnosisCount == 0) {
            throw new BizException(ErrorCode.B2003);
        }
        visit.setStatus(30);
        visit.setEndTime(LocalDateTime.now());
        visitMapper.updateById(visit);
    }

    /** 就诊详情聚合（C-11） */
    public VisitDetailResponse detail(Long visitId) {
        CliVisit visit = requireVisit(visitId);
        checkVisitScope(visit);
        VisitDetailResponse resp = new VisitDetailResponse();
        resp.setId(visit.getId());
        resp.setVisitNo(visit.getVisitNo());
        resp.setRegistrationId(visit.getRegistrationId());
        PatientDTO patient = patientAppService.getById(visit.getPatientId());
        if (patient != null) {
            VisitDetailResponse.PatientInfo info = new VisitDetailResponse.PatientInfo();
            info.setId(patient.getId());
            info.setPatientNo(patient.getPatientNo());
            info.setName(patient.getName());
            info.setGender(patient.getGender());
            info.setBirthDate(patient.getBirthDate());
            info.setAllergyHistory(patient.getAllergyHistory());
            resp.setPatient(info);
        }
        DoctorDTO doctor = basedataAppService.getDoctor(visit.getDoctorId());
        resp.setDoctorName(doctor == null ? null : doctor.getDoctorName());
        var dept = basedataAppService.getDepartment(visit.getDeptId());
        resp.setDeptName(dept == null ? null : dept.getDeptName());
        resp.setVisitDate(visit.getVisitDate());
        resp.setChiefComplaint(visit.getChiefComplaint());
        resp.setPresentIllness(visit.getPresentIllness());
        resp.setPhysicalExam(visit.getPhysicalExam());
        resp.setAdvice(visit.getAdvice());
        resp.setStatus(visit.getStatus());
        resp.setStartTime(visit.getStartTime());
        resp.setEndTime(visit.getEndTime());

        resp.setDiagnoses(diagnosisMapper.selectList(new LambdaQueryWrapper<CliDiagnosis>()
                        .eq(CliDiagnosis::getVisitId, visitId)
                        .eq(CliDiagnosis::getStatus, 1)
                        .orderByAsc(CliDiagnosis::getDiagnosisType))
                .stream().map(d -> {
                    VisitDetailResponse.DiagnosisItem item = new VisitDetailResponse.DiagnosisItem();
                    item.setId(d.getId());
                    item.setDiagnosisCode(d.getDiagnosisCode());
                    item.setDiagnosisName(d.getDiagnosisName());
                    item.setDiagnosisType(d.getDiagnosisType());
                    return item;
                }).toList());

        resp.setOrders(orderMapper.selectList(new LambdaQueryWrapper<CliMedicalOrder>()
                        .eq(CliMedicalOrder::getVisitId, visitId)
                        .eq(CliMedicalOrder::getStatus, 1)
                        .orderByAsc(CliMedicalOrder::getId))
                .stream().map(o -> {
                    VisitDetailResponse.OrderItem item = new VisitDetailResponse.OrderItem();
                    item.setId(o.getId());
                    item.setOrderType(o.getOrderType());
                    item.setContent(o.getContent());
                    item.setCreatedAt(o.getCreatedAt());
                    return item;
                }).toList());

        List<CliPrescription> prescriptions = prescriptionMapper.selectList(
                new LambdaQueryWrapper<CliPrescription>()
                        .eq(CliPrescription::getVisitId, visitId)
                        .orderByDesc(CliPrescription::getId));
        resp.setPrescriptions(prescriptions.stream().map(rx -> {
            VisitDetailResponse.PrescriptionDetail detail = new VisitDetailResponse.PrescriptionDetail();
            detail.setId(rx.getId());
            detail.setRxNo(rx.getRxNo());
            detail.setTotalAmount(rx.getTotalAmount());
            detail.setStatus(rx.getStatus());
            detail.setChargeStatus(rx.getChargeStatus());
            detail.setReviewComment(rx.getReviewComment());
            detail.setVoidReason(rx.getVoidReason());
            detail.setItems(prescriptionItemMapper.selectList(new LambdaQueryWrapper<CliPrescriptionItem>()
                            .eq(CliPrescriptionItem::getPrescriptionId, rx.getId())
                            .eq(CliPrescriptionItem::getStatus, 1)
                            .orderByAsc(CliPrescriptionItem::getId))
                    .stream().map(this::toItemDetail).toList());
            return detail;
        }).toList());

        resp.setExamApplications(examApplicationMapper.selectList(
                        new LambdaQueryWrapper<CliExamApplication>()
                                .eq(CliExamApplication::getVisitId, visitId)
                                .orderByDesc(CliExamApplication::getId))
                .stream().map(app -> {
                    VisitDetailResponse.ExamItem item = new VisitDetailResponse.ExamItem();
                    item.setId(app.getId());
                    item.setApplyNo(app.getApplyNo());
                    item.setApplyType(app.getApplyType());
                    item.setPrice(app.getPrice());
                    item.setChargeStatus(app.getChargeStatus());
                    item.setStatus(app.getStatus());
                    ChargeItemDTO chargeItem = basedataAppService.getChargeItem(app.getChargeItemId());
                    item.setItemName(chargeItem == null ? null : chargeItem.getItemName());
                    return item;
                }).toList());
        return resp;
    }

    /** 历史就诊分页（C-12）：医生仅本人，管理员全量；批量解析患者/医生/科室名 */
    public PageResult<VisitBrief> page(VisitPageQuery query) {
        LambdaQueryWrapper<CliVisit> wrapper = new LambdaQueryWrapper<CliVisit>()
                .eq(query.getPatientId() != null, CliVisit::getPatientId, query.getPatientId())
                .eq(query.getDeptId() != null, CliVisit::getDeptId, query.getDeptId())
                .eq(query.getVisitDate() != null, CliVisit::getVisitDate, query.getVisitDate())
                .eq(query.getStatus() != null, CliVisit::getStatus, query.getStatus())
                .orderByDesc(CliVisit::getId);
        LoginUser user = CurrentUser.get();
        if (!user.getRoleCodes().contains("ADMIN")) {
            wrapper.eq(CliVisit::getDoctorId, currentDoctorId(query.getDoctorId()));
        } else {
            wrapper.eq(query.getDoctorId() != null, CliVisit::getDoctorId, query.getDoctorId());
        }
        Page<CliVisit> page = visitMapper.selectPage(query.toPage(), wrapper);
        List<CliVisit> records = page.getRecords();
        Map<Long, PatientDTO> patientMap = patientAppService.listByIds(
                        records.stream().map(CliVisit::getPatientId).distinct().toList()).stream()
                .collect(Collectors.toMap(PatientDTO::getId, p -> p));
        Map<Long, DoctorDTO> doctorMap = basedataAppService.doctorMap();
        Map<Long, DepartmentDTO> deptMap = basedataAppService.departmentMap();
        PageResult<VisitBrief> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(records.stream().map(visit -> {
            VisitBrief brief = new VisitBrief();
            brief.setId(visit.getId());
            brief.setVisitNo(visit.getVisitNo());
            brief.setPatientId(visit.getPatientId());
            PatientDTO patient = patientMap.get(visit.getPatientId());
            brief.setPatientName(patient == null ? null : patient.getName());
            brief.setDoctorId(visit.getDoctorId());
            DoctorDTO doctor = doctorMap.get(visit.getDoctorId());
            brief.setDoctorName(doctor == null ? null : doctor.getDoctorName());
            brief.setDeptId(visit.getDeptId());
            DepartmentDTO dept = deptMap.get(visit.getDeptId());
            brief.setDeptName(dept == null ? null : dept.getDeptName());
            brief.setVisitDate(visit.getVisitDate());
            brief.setStatus(visit.getStatus());
            brief.setStartTime(visit.getStartTime());
            brief.setEndTime(visit.getEndTime());
            return brief;
        }).toList());
        return result;
    }

    private VisitDetailResponse.PrescriptionItemDetail toItemDetail(CliPrescriptionItem row) {
        VisitDetailResponse.PrescriptionItemDetail detail = new VisitDetailResponse.PrescriptionItemDetail();
        detail.setId(row.getId());
        detail.setDrugId(row.getDrugId());
        detail.setDrugName(row.getDrugName());
        detail.setSpec(row.getSpec());
        detail.setDosage(row.getDosage());
        detail.setFrequency(row.getFrequency());
        detail.setUsageRoute(row.getUsageRoute());
        detail.setDays(row.getDays());
        detail.setQuantity(row.getQuantity());
        detail.setUnit(row.getUnit());
        detail.setUnitPrice(row.getUnitPrice());
        detail.setAmount(row.getAmount());
        detail.setUsageNote(row.getUsageNote());
        return detail;
    }

    private CliVisit requireVisit(Long visitId) {
        CliVisit visit = visitMapper.selectById(visitId);
        if (visit == null) {
            throw new BizException(ErrorCode.B2007);
        }
        return visit;
    }

    /** 就诊必须处于接诊中：已完成 → B2001，其余 → B2002 */
    private CliVisit requireVisitInProgress(Long visitId) {
        CliVisit visit = requireVisit(visitId);
        checkVisitScope(visit);
        if (visit.getStatus() == 30) {
            throw new BizException(ErrorCode.B2001);
        }
        if (visit.getStatus() != 20) {
            throw new BizException(ErrorCode.B2002);
        }
        return visit;
    }

    /** 当前登录用户对应医生；非医生且非管理员 → A0003 */
    private Long currentDoctorId(Long overrideDoctorId) {
        LoginUser user = CurrentUser.get();
        DoctorDTO doctor = basedataAppService.getDoctorByUserId(user.getUserId());
        if (doctor != null) {
            if (overrideDoctorId != null && !user.getRoleCodes().contains("ADMIN")
                    && !doctor.getId().equals(overrideDoctorId)) {
                throw new BizException(ErrorCode.A0003);
            }
            return doctor.getId();
        }
        if (user.getRoleCodes().contains("ADMIN")) {
            return overrideDoctorId;
        }
        throw new BizException(ErrorCode.A0003, "当前用户未绑定医生");
    }

    private void checkRegistrationScope(RegistrationDTO reg) {
        LoginUser user = CurrentUser.get();
        if (user.getRoleCodes().contains("ADMIN")) {
            return;
        }
        DoctorDTO doctor = basedataAppService.getDoctorByUserId(user.getUserId());
        if (doctor == null || !doctor.getId().equals(reg.getDoctorId())) {
            throw new BizException(ErrorCode.A0003);
        }
    }

    private void checkVisitScope(CliVisit visit) {
        LoginUser user = CurrentUser.get();
        if (user.getRoleCodes().contains("ADMIN")) {
            return;
        }
        DoctorDTO doctor = basedataAppService.getDoctorByUserId(user.getUserId());
        if (doctor == null || !doctor.getId().equals(visit.getDoctorId())) {
            throw new BizException(ErrorCode.A0003);
        }
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
