package com.his.modules.clinic.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.clinic.entity.CliExamApplication;
import com.his.modules.clinic.entity.CliPrescription;
import com.his.modules.clinic.entity.CliPrescriptionItem;
import com.his.modules.clinic.entity.CliVisit;
import com.his.modules.clinic.app.VisitStatDTO;
import com.his.modules.clinic.mapper.CliExamApplicationMapper;
import com.his.modules.clinic.mapper.CliPrescriptionItemMapper;
import com.his.modules.clinic.mapper.CliPrescriptionMapper;
import com.his.modules.clinic.mapper.CliVisitMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/**
 * 医生工作站跨模块应用服务：收费结算（B-01/B-02/B-05）与药房库存（F-01~F-06）
 * 统一经本服务读写 cli_ 表（模块边界：《01》§4.1）。
 */
@Service
@RequiredArgsConstructor
public class ClinicAppService {
    private final CliVisitMapper visitMapper;
    private final CliPrescriptionMapper prescriptionMapper;
    private final CliPrescriptionItemMapper prescriptionItemMapper;
    private final CliExamApplicationMapper examApplicationMapper;
    private final com.his.modules.ris.mapper.RisRequestMapper risRequestMapper;
    private final PatientAppService patientAppService;
    private final BasedataAppService basedataAppService;

    // ---------------- 收费结算视角 ----------------

    public BillingVisitDTO getVisitForBilling(Long visitId) {
        CliVisit visit = visitMapper.selectById(visitId);
        return visit == null ? null : toBillingVisit(visit);
    }

    /** 按挂号单查就诊（收费窗口待缴费列表用） */
    public BillingVisitDTO getVisitByRegistrationId(Long registrationId) {
        CliVisit visit = visitMapper.selectByRegistrationId(registrationId);
        return visit == null ? null : toBillingVisit(visit);
    }

    /** 就诊下未收费处方明细（处方状态=待审核/审核通过） */
    public List<BillingRxItemDTO> listUnpaidPrescriptionItems(Long visitId) {
        List<Long> rxIds = prescriptionMapper.selectList(new LambdaQueryWrapper<CliPrescription>()
                        .eq(CliPrescription::getVisitId, visitId)
                        .in(CliPrescription::getStatus, 10, 20)
                        .eq(CliPrescription::getChargeStatus, 0))
                .stream().map(CliPrescription::getId).toList();
        if (rxIds.isEmpty()) {
            return List.of();
        }
        Map<Long, CliPrescription> rxById = prescriptionMapper.selectBatchIds(rxIds).stream()
                .collect(java.util.stream.Collectors.toMap(CliPrescription::getId, r -> r));
        return prescriptionItemMapper.selectList(new LambdaQueryWrapper<CliPrescriptionItem>()
                        .in(CliPrescriptionItem::getPrescriptionId, rxIds)
                        .eq(CliPrescriptionItem::getStatus, 1)
                        .orderByAsc(CliPrescriptionItem::getId))
                .stream().map(row -> {
                    BillingRxItemDTO dto = new BillingRxItemDTO();
                    dto.setPrescriptionItemId(row.getId());
                    dto.setPrescriptionId(row.getPrescriptionId());
                    CliPrescription rx = rxById.get(row.getPrescriptionId());
                    dto.setRxNo(rx == null ? null : rx.getRxNo());
                    dto.setDrugId(row.getDrugId());
                    dto.setDrugName(row.getDrugName());
                    dto.setQuantity(row.getQuantity());
                    dto.setUnitPrice(row.getUnitPrice());
                    dto.setAmount(row.getAmount());
                    return dto;
                }).toList();
    }

    /** 就诊下未收费检查/检验申请 */
    public List<BillingExamDTO> listUnpaidExamApplications(Long visitId) {
        return examApplicationMapper.selectList(new LambdaQueryWrapper<CliExamApplication>()
                        .eq(CliExamApplication::getVisitId, visitId)
                        .eq(CliExamApplication::getStatus, 10)
                        .eq(CliExamApplication::getChargeStatus, 0)
                        .orderByAsc(CliExamApplication::getId))
                .stream().map(app -> {
                    BillingExamDTO dto = new BillingExamDTO();
                    dto.setExamId(app.getId());
                    dto.setApplyNo(app.getApplyNo());
                    ChargeItemDTO item = basedataAppService.getChargeItem(app.getChargeItemId());
                    dto.setItemName(item == null ? null : item.getItemName());
                    dto.setCategory(item == null ? null : item.getCategory());
                    dto.setPrice(app.getPrice());
                    return dto;
                }).toList();
    }

    /** 存在未收费处方/申请的就诊（收费窗口待缴费列表，最多返回 100 条） */
    public List<UnpaidVisitDTO> listVisitsWithUnpaidItems() {
        List<Long> visitIds = new java.util.ArrayList<>();
        prescriptionMapper.selectList(new LambdaQueryWrapper<CliPrescription>()
                        .select(CliPrescription::getVisitId)
                        .in(CliPrescription::getStatus, 10, 20)
                        .eq(CliPrescription::getChargeStatus, 0)
                        .orderByDesc(CliPrescription::getId))
                .forEach(rx -> visitIds.add(rx.getVisitId()));
        examApplicationMapper.selectList(new LambdaQueryWrapper<CliExamApplication>()
                        .select(CliExamApplication::getVisitId)
                        .eq(CliExamApplication::getStatus, 10)
                        .eq(CliExamApplication::getChargeStatus, 0)
                        .orderByDesc(CliExamApplication::getId))
                .forEach(app -> visitIds.add(app.getVisitId()));
        // 批量取数（三十二轮性能：原逐条 selectById/getById/getDoctor，100 条 ≈ 300 次单查 → 3 次批查）
        List<Long> topIds = visitIds.stream().distinct().limit(100)
                .sorted(java.util.Comparator.reverseOrder()).toList();
        Map<Long, CliVisit> visits = topIds.isEmpty() ? Map.of()
                : visitMapper.selectBatchIds(topIds).stream()
                        .collect(java.util.stream.Collectors.toMap(CliVisit::getId, v -> v));
        Map<Long, PatientDTO> patients = new java.util.LinkedHashMap<>();
        List<Long> patientIds = visits.values().stream()
                .map(CliVisit::getPatientId).distinct().toList();
        for (PatientDTO p : patientAppService.listByIds(patientIds)) {
            patients.put(p.getId(), p);
        }
        List<Long> doctorIds = visits.values().stream()
                .map(CliVisit::getDoctorId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, DoctorDTO> doctors = basedataAppService.listDoctorsByIds(doctorIds);
        return topIds.stream().map(visits::get).filter(java.util.Objects::nonNull)
                .map(v -> {
                    UnpaidVisitDTO dto = new UnpaidVisitDTO();
                    dto.setVisitId(v.getId());
                    dto.setVisitNo(v.getVisitNo());
                    dto.setVisitDate(v.getVisitDate());
                    PatientDTO patient = patients.get(v.getPatientId());
                    dto.setPatientName(patient == null ? null : patient.getName());
                    DoctorDTO doctor = doctors.get(v.getDoctorId());
                    dto.setDoctorName(doctor == null ? null : doctor.getDoctorName());
                    return dto;
                }).toList();
    }

    /** 处方标记已收费（乐观条件更新，仅未收费状态生效）；返回实际更新行数供调用方断言 */
    @Transactional
    public int markPrescriptionsCharged(Collection<Long> rxIds) {
        if (rxIds == null || rxIds.isEmpty()) {
            return 0;
        }
        return prescriptionMapper.update(null, new LambdaUpdateWrapper<CliPrescription>()
                .in(CliPrescription::getId, rxIds)
                .eq(CliPrescription::getChargeStatus, 0)
                .set(CliPrescription::getChargeStatus, 1)
                .set(CliPrescription::getUpdatedAt, java.time.LocalDateTime.now()));
    }

    /** 检查申请标记已收费（状态 10→20）；返回实际更新行数供调用方断言 */
    @Transactional
    public int markExamApplicationsCharged(Collection<Long> applyIds) {
        if (applyIds == null || applyIds.isEmpty()) {
            return 0;
        }
        return examApplicationMapper.update(null, new LambdaUpdateWrapper<CliExamApplication>()
                .in(CliExamApplication::getId, applyIds)
                .eq(CliExamApplication::getStatus, 10)
                .eq(CliExamApplication::getChargeStatus, 0)
                .set(CliExamApplication::getStatus, 20)
                .set(CliExamApplication::getChargeStatus, 1)
                .set(CliExamApplication::getUpdatedAt, java.time.LocalDateTime.now()));
    }

    /** 退费快照：clinicStatus=检查申请状态；risStatus=关联 RIS 检查单状态（无单则缺省） */
    public record ExamRefundSnapshot(Map<Long, Integer> clinicStatus, Map<Long, Integer> risStatus) {
    }

    /**
     * 退费前置：按 RIS → 检查申请的统一顺序行锁，读取真实状态。
     * 与 RisService 的预约/开始/报告发布互斥，堵住"退费校验后检查仍被预约/执行"的窗口。
     */
    public ExamRefundSnapshot lockExamsForRefund(Collection<Long> applyIds) {
        Map<Long, Integer> clinicStatus = new LinkedHashMap<>();
        Map<Long, Integer> risStatus = new LinkedHashMap<>();
        if (applyIds == null || applyIds.isEmpty()) {
            return new ExamRefundSnapshot(clinicStatus, risStatus);
        }
        List<Long> ids = applyIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        // 非锁定读仅用于发现关联 RIS 单，随后统一按 RIS → 检查申请顺序加锁
        Map<Long, Long> risIdByApply = new HashMap<>();
        for (CliExamApplication exam : examApplicationMapper.selectBatchIds(ids)) {
            if (exam.getRisRequestId() != null) {
                risIdByApply.put(exam.getId(), exam.getRisRequestId());
            }
        }
        Map<Long, Integer> lockedRis = new HashMap<>();
        for (Long risId : risIdByApply.values().stream().distinct().sorted().toList()) {
            com.his.modules.ris.entity.RisRequest ris = risRequestMapper.selectByIdForUpdate(risId);
            if (ris != null) {
                lockedRis.put(risId, ris.getStatus());
            }
        }
        for (Long applyId : ids) {
            CliExamApplication exam = examApplicationMapper.selectByIdForUpdate(applyId);
            if (exam == null) {
                continue;
            }
            clinicStatus.put(applyId, exam.getStatus());
            if (exam.getRisRequestId() != null) {
                Integer status = lockedRis.get(exam.getRisRequestId());
                if (status == null) {
                    // 加锁窗口内新绑定（收费联动）的 RIS 单：补锁后再读
                    com.his.modules.ris.entity.RisRequest ris =
                            risRequestMapper.selectByIdForUpdate(exam.getRisRequestId());
                    status = ris == null ? null : ris.getStatus();
                }
                if (status != null) {
                    risStatus.put(applyId, status);
                }
            }
        }
        return new ExamRefundSnapshot(clinicStatus, risStatus);
    }

    /**
     * 退费联动作废检查申请：检查申请 20→50，并作废未预约(10)的 RIS 检查单。
     * 返回 false 表示状态已变化且无法安全作废，调用方必须回滚退费（不得静默放行）。
     */
    @Transactional
    public boolean voidExamForRefund(Long applyId) {
        CliExamApplication exam = examApplicationMapper.selectByIdForUpdate(applyId);
        if (exam == null) {
            return false;
        }
        if (exam.getStatus() == null || exam.getStatus() != 50) {
            int updated = examApplicationMapper.update(null, new LambdaUpdateWrapper<CliExamApplication>()
                    .eq(CliExamApplication::getId, applyId)
                    .eq(CliExamApplication::getStatus, 20)
                    .set(CliExamApplication::getStatus, 50)
                    .set(CliExamApplication::getUpdatedAt, java.time.LocalDateTime.now()));
            if (updated != 1) {
                return false;
            }
        }
        if (exam.getRisRequestId() != null) {
            risRequestMapper.update(null, new LambdaUpdateWrapper<com.his.modules.ris.entity.RisRequest>()
                    .eq(com.his.modules.ris.entity.RisRequest::getId, exam.getRisRequestId())
                    .eq(com.his.modules.ris.entity.RisRequest::getStatus, 10)
                    .set(com.his.modules.ris.entity.RisRequest::getStatus, 50)
                    .set(com.his.modules.ris.entity.RisRequest::getUpdatedAt, java.time.LocalDateTime.now()));
            com.his.modules.ris.entity.RisRequest ris =
                    risRequestMapper.selectByIdForUpdate(exam.getRisRequestId());
            // 已预约/检查中/已报告不可作废；已作废视为幂等成功
            if (ris == null || ris.getStatus() == null || ris.getStatus() != 50) {
                return false;
            }
        }
        return true;
    }

    /** 退费前置：按 ID 升序行锁处方（与发药事务锁同一行，消除"校验后发药"窗口） */
    public Map<Long, Integer> lockPrescriptionsForRefund(Collection<Long> rxIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (rxIds == null || rxIds.isEmpty()) {
            return result;
        }
        for (Long rxId : rxIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            CliPrescription rx = prescriptionMapper.selectByIdForUpdate(rxId);
            if (rx != null) {
                result.put(rxId, rx.getStatus());
            }
        }
        return result;
    }

    /** 处方 ID → 处方号（一百一十八轮性能【2】：发药/退药单分页批量取号，替代逐行视图查询） */
    public Map<Long, String> prescriptionRxNoByIds(Collection<Long> rxIds) {
        if (rxIds == null || rxIds.isEmpty()) {
            return new HashMap<>();
        }
        return prescriptionMapper.selectBatchIds(rxIds).stream()
                .collect(java.util.stream.Collectors.toMap(CliPrescription::getId, CliPrescription::getRxNo));
    }

    /** 处方 ID → 处方状态（一百一十三轮 A-P3-1：发药单列表批量判定已整方退药 status=50） */
    public Map<Long, Integer> prescriptionStatusByIds(Collection<Long> rxIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (rxIds == null || rxIds.isEmpty()) {
            return result;
        }
        for (CliPrescription rx : prescriptionMapper.selectBatchIds(rxIds)) {
            result.put(rx.getId(), rx.getStatus());
        }
        return result;
    }

    /** 处方明细 → 所属处方 ID */
    public Map<Long, Long> prescriptionIdByItemIds(Collection<Long> itemIds) {
        Map<Long, Long> result = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        for (CliPrescriptionItem row : prescriptionItemMapper.selectBatchIds(itemIds)) {
            result.put(row.getId(), row.getPrescriptionId());
        }
        return result;
    }

    /**
     * 退费联动作废处方（《05》R9：仅未发药处方，幂等）。
     * 返回 false 表示处方已发药/不存在，调用方必须回滚退费；已作废(50) 视为幂等成功。
     * 驳回(40) 也必须可作废：处方收费后药师仍可驳回，若只认 10/20，患者已付的药品费将永远退不掉。
     */
    @Transactional
    public boolean voidPrescriptionForRefund(Long rxId) {
        int voided = prescriptionMapper.update(null, new LambdaUpdateWrapper<CliPrescription>()
                .eq(CliPrescription::getId, rxId)
                .in(CliPrescription::getStatus, 10, 20, 40)
                .set(CliPrescription::getStatus, 50)
                .set(CliPrescription::getVoidReason, "退费"));
        if (voided == 1) {
            return true;
        }
        CliPrescription rx = prescriptionMapper.selectById(rxId);
        return rx != null && rx.getStatus() != null && rx.getStatus() == 50;
    }

    // ---------------- 只读统计（报表模块 T-02） ----------------

    /** 日门诊量（按就诊完成时间） */
    public List<VisitStatDTO> dailyVisits(LocalDate start, LocalDate end) {
        return visitMapper.dailyVisits(start, end);
    }

    // ---------------- 药房库存视角 ----------------

    /** 药房队列：按状态查处方（含展示明细，最多 100 条）。
     *  一百一十八轮性能【1】P2：批量预取 visit/医生/科室/患者/明细——原逐行 toRxDisplay
     *  每行 7 次单查，队列满 100 条 ≈700 查（秒级卡顿） */
    public List<RxDisplayDTO> listPrescriptionsByStatus(Integer status, Integer chargeStatus) {
        List<CliPrescription> rxs = prescriptionMapper.selectList(new LambdaQueryWrapper<CliPrescription>()
                .eq(status != null, CliPrescription::getStatus, status)
                .eq(chargeStatus != null, CliPrescription::getChargeStatus, chargeStatus)
                .orderByDesc(CliPrescription::getId)
                .last("LIMIT 100"));
        if (rxs.isEmpty()) {
            return List.of();
        }
        // 批量预取（5 次查询替代至多 700 次）
        List<Long> visitIds = rxs.stream().map(CliPrescription::getVisitId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, CliVisit> visits = visitIds.isEmpty() ? Map.of()
                : visitMapper.selectBatchIds(visitIds).stream()
                        .collect(java.util.stream.Collectors.toMap(CliVisit::getId, v -> v));
        java.util.Set<Long> doctorIds = visits.values().stream()
                .map(CliVisit::getDoctorId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, DoctorDTO> doctors = basedataAppService.listDoctorsByIds(doctorIds);
        java.util.Set<Long> deptIds = visits.values().stream()
                .map(CliVisit::getDeptId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, ? extends com.his.modules.basedata.app.DepartmentDTO> depts = deptIds.isEmpty()
                ? Map.of() : basedataAppService.departmentMap();
        List<Long> patientIds = rxs.stream().map(CliPrescription::getPatientId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, PatientDTO> patients = new HashMap<>();
        for (PatientDTO pt : patientAppService.listByIds(patientIds)) {
            patients.put(pt.getId(), pt);
        }
        List<Long> rxIds = rxs.stream().map(CliPrescription::getId).toList();
        Map<Long, List<RxDisplayDTO.Item>> itemsByRx = prescriptionItemMapper.selectList(
                        new LambdaQueryWrapper<CliPrescriptionItem>()
                                .in(CliPrescriptionItem::getPrescriptionId, rxIds)
                                .eq(CliPrescriptionItem::getStatus, 1)
                                .orderByAsc(CliPrescriptionItem::getId))
                .stream().collect(java.util.stream.Collectors.groupingBy(CliPrescriptionItem::getPrescriptionId,
                        java.util.stream.Collectors.mapping(this::toRxItem, java.util.stream.Collectors.toList())));
        return rxs.stream().map(rx -> {
            RxDisplayDTO dto = new RxDisplayDTO();
            dto.setId(rx.getId());
            dto.setRxNo(rx.getRxNo());
            dto.setVisitId(rx.getVisitId());
            dto.setPatientId(rx.getPatientId());
            dto.setTotalAmount(rx.getTotalAmount());
            dto.setStatus(rx.getStatus());
            dto.setChargeStatus(rx.getChargeStatus());
            dto.setCreatedAt(rx.getCreatedAt());
            CliVisit visit = visits.get(rx.getVisitId());
            if (visit != null) {
                dto.setVisitNo(visit.getVisitNo());
                DoctorDTO doctor = doctors.get(visit.getDoctorId());
                dto.setDoctorName(doctor == null ? null : doctor.getDoctorName());
                var dept = depts.get(visit.getDeptId());
                dto.setDeptName(dept == null ? null : dept.getDeptName());
            }
            PatientDTO patient = patients.get(rx.getPatientId());
            if (patient != null) {
                dto.setPatientName(patient.getName());
                dto.setPatientNo(patient.getPatientNo());
            }
            dto.setItems(itemsByRx.getOrDefault(rx.getId(), List.of()));
            return dto;
        }).toList();
    }

    /** 明细行 → DTO 条目（批量化共用，一百一十八轮性能【1】） */
    private RxDisplayDTO.Item toRxItem(CliPrescriptionItem row) {
        RxDisplayDTO.Item item = new RxDisplayDTO.Item();
        item.setItemId(row.getId());
        item.setDrugId(row.getDrugId());
        item.setDrugName(row.getDrugName());
        item.setSpec(row.getSpec());
        item.setDosage(row.getDosage());
        item.setFrequency(row.getFrequency());
        item.setUsageRoute(row.getUsageRoute());
        item.setDays(row.getDays());
        item.setQuantity(row.getQuantity());
        item.setUnit(row.getUnit());
        item.setUnitPrice(row.getUnitPrice());
        item.setAmount(row.getAmount());
        item.setUsageNote(row.getUsageNote());
        return item;
    }

    /** 可发药队列：审核通过且已收费 */
    public List<RxDisplayDTO> listDispensablePrescriptions() {
        return listPrescriptionsByStatus(20, 1);
    }

    /** 药师审核（状态 10→20/40，写审核字段；重复审核抛 B4008）。
     *  一百一十三轮 A-P1-1：条件更新断言——原"快照读校验+无条件 updateById"下，
     *  双药师并发双留痕，且与发药并发时会把已发药(30)处方改回 20/40（状态机错乱） */
    @Transactional
    public void reviewPrescription(Long rxId, boolean pass, Long reviewerId, String comment) {
        CliPrescription rx = prescriptionMapper.selectById(rxId);
        if (rx == null) {
            throw new BizException(ErrorCode.A0001, "处方不存在");
        }
        if (rx.getStatus() != 10) {
            throw new BizException(ErrorCode.B4008);
        }
        rx.setStatus(pass ? 20 : 40);
        rx.setReviewBy(reviewerId);
        rx.setReviewAt(java.time.LocalDateTime.now());
        rx.setReviewComment(comment);
        int updated = prescriptionMapper.update(rx,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CliPrescription>()
                        .eq(CliPrescription::getId, rxId)
                        .eq(CliPrescription::getStatus, 10)
                        .set(CliPrescription::getStatus, rx.getStatus())
                        .set(CliPrescription::getReviewBy, reviewerId)
                        .set(CliPrescription::getReviewAt, rx.getReviewAt())
                        .set(CliPrescription::getReviewComment, comment));
        if (updated != 1) {
            throw new BizException(ErrorCode.B4008);
        }
    }

    /** 只读处方视图（分页展示用，无行锁——展示查询不加 FOR UPDATE） */
    public PharmacyRxDTO getPrescriptionView(Long rxId) {
        CliPrescription rx = prescriptionMapper.selectById(rxId);
        return rx == null ? null : toPharmacyRx(rx);
    }

    /** 行锁读取处方（发药事务第一步，《03》§4.2） */
    @Transactional(propagation = Propagation.SUPPORTS)
    public PharmacyRxDTO getPrescriptionForDispense(Long rxId) {
        CliPrescription rx = prescriptionMapper.selectByIdForUpdate(rxId);
        return rx == null ? null : toPharmacyRx(rx);
    }

    private PharmacyRxDTO toPharmacyRx(CliPrescription rx) {
        PharmacyRxDTO dto = new PharmacyRxDTO();
        dto.setId(rx.getId());
        dto.setRxNo(rx.getRxNo());
        dto.setPatientId(rx.getPatientId());
        dto.setVisitId(rx.getVisitId());
        dto.setStatus(rx.getStatus());
        dto.setChargeStatus(rx.getChargeStatus());
        dto.setTotalAmount(rx.getTotalAmount());
        dto.setItems(prescriptionItemMapper.selectList(new LambdaQueryWrapper<CliPrescriptionItem>()
                        .eq(CliPrescriptionItem::getPrescriptionId, rx.getId())
                        .eq(CliPrescriptionItem::getStatus, 1)
                        .orderByAsc(CliPrescriptionItem::getId))
                .stream().map(row -> {
                    PharmacyRxDTO.Item item = new PharmacyRxDTO.Item();
                    item.setItemId(row.getId());
                    item.setDrugId(row.getDrugId());
                    item.setDrugName(row.getDrugName());
                    item.setQuantity(row.getQuantity());
                    return item;
                }).toList());
        return dto;
    }

    /** 发药成功：处方 20→30（乐观锁更新） */
    @Transactional
    public void markDispensed(Long rxId) {
        CliPrescription rx = prescriptionMapper.selectById(rxId);
        if (rx == null || rx.getStatus() != 20) {
            throw new BizException(ErrorCode.B4003);
        }
        rx.setStatus(30);
        prescriptionMapper.updateById(rx);
    }

    /** 整方退药：处方 30→50（void_reason=退药），恢复该方药品费可退 */
    @Transactional
    public void markPrescriptionReturned(Long rxId) {
        // 条件更新断言前态：快照读+无断言更新在并发双跑下会双倍回补库存（八十六轮并发审计）
        int returned = prescriptionMapper.update(null, new LambdaUpdateWrapper<CliPrescription>()
                .eq(CliPrescription::getId, rxId)
                .eq(CliPrescription::getStatus, 30)
                .set(CliPrescription::getStatus, 50)
                .set(CliPrescription::getVoidReason, "退药"));
        if (returned != 1) {
            throw new BizException(ErrorCode.B4007);
        }
    }

    private BillingVisitDTO toBillingVisit(CliVisit visit) {
        BillingVisitDTO dto = new BillingVisitDTO();
        dto.setId(visit.getId());
        dto.setVisitNo(visit.getVisitNo());
        dto.setRegistrationId(visit.getRegistrationId());
        dto.setPatientId(visit.getPatientId());
        dto.setVisitStatus(visit.getStatus());
        dto.setVisitDate(visit.getVisitDate());
        PatientDTO patient = patientAppService.getById(visit.getPatientId());
        dto.setPatientName(patient == null ? null : patient.getName());
        DoctorDTO doctor = basedataAppService.getDoctor(visit.getDoctorId());
        dto.setDoctorName(doctor == null ? null : doctor.getDoctorName());
        return dto;
    }

    private RxDisplayDTO toRxDisplay(CliPrescription rx) {
        RxDisplayDTO dto = new RxDisplayDTO();
        dto.setId(rx.getId());
        dto.setRxNo(rx.getRxNo());
        dto.setVisitId(rx.getVisitId());
        dto.setPatientId(rx.getPatientId());
        dto.setTotalAmount(rx.getTotalAmount());
        dto.setStatus(rx.getStatus());
        dto.setChargeStatus(rx.getChargeStatus());
        dto.setCreatedAt(rx.getCreatedAt());
        CliVisit visit = visitMapper.selectById(rx.getVisitId());
        if (visit != null) {
            dto.setVisitNo(visit.getVisitNo());
            DoctorDTO doctor = basedataAppService.getDoctor(visit.getDoctorId());
            dto.setDoctorName(doctor == null ? null : doctor.getDoctorName());
            var dept = basedataAppService.getDepartment(visit.getDeptId());
            dto.setDeptName(dept == null ? null : dept.getDeptName());
        }
        PatientDTO patient = patientAppService.getById(rx.getPatientId());
        if (patient != null) {
            dto.setPatientName(patient.getName());
            dto.setPatientNo(patient.getPatientNo());
        }
        dto.setItems(prescriptionItemMapper.selectList(new LambdaQueryWrapper<CliPrescriptionItem>()
                        .eq(CliPrescriptionItem::getPrescriptionId, rx.getId())
                        .eq(CliPrescriptionItem::getStatus, 1)
                        .orderByAsc(CliPrescriptionItem::getId))
                .stream().map(row -> {
                    RxDisplayDTO.Item item = new RxDisplayDTO.Item();
                    item.setItemId(row.getId());
                    item.setDrugId(row.getDrugId());
                    item.setDrugName(row.getDrugName());
                    item.setSpec(row.getSpec());
                    item.setDosage(row.getDosage());
                    item.setFrequency(row.getFrequency());
                    item.setUsageRoute(row.getUsageRoute());
                    item.setDays(row.getDays());
                    item.setQuantity(row.getQuantity());
                    item.setUnit(row.getUnit());
                    item.setUnitPrice(row.getUnitPrice());
                    item.setAmount(row.getAmount());
                    item.setUsageNote(row.getUsageNote());
                    return item;
                }).toList());
        return dto;
    }
}
