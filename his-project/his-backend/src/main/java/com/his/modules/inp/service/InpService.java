package com.his.modules.inp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.basedata.app.DepartmentDTO;
import com.his.modules.billing.entity.BilChargeBill;
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.dto.DepositRefundRequest;
import com.his.modules.inp.dto.AdmissionCreateRequest;
import com.his.modules.inp.dto.AdmissionQuery;
import com.his.modules.inp.dto.AdmissionResponse;
import com.his.modules.inp.dto.BedVO;
import com.his.modules.inp.dto.DepositRequest;
import com.his.modules.inp.dto.DischargeRequest;
import com.his.modules.inp.dto.FeeGroupResponse;
import com.his.modules.inp.dto.ManualFeeRequest;
import com.his.modules.inp.dto.TransferRequest;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.entity.InpBed;
import com.his.modules.inp.entity.InpDailyFee;
import com.his.modules.inp.entity.InpDeposit;
import com.his.modules.inp.entity.InpTransfer;
import com.his.modules.inp.entity.InpWard;
import com.his.modules.inp.mapper.InpAdmissionMapper;
import com.his.modules.inp.mapper.InpBedMapper;
import com.his.modules.inp.mapper.InpDailyFeeMapper;
import com.his.modules.inp.mapper.InpDepositMapper;
import com.his.modules.inp.mapper.InpTransferMapper;
import com.his.modules.inp.mapper.InpWardMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 住院管理服务（I-01~I-11，状态机《10》§2.1/2.2，规则见《09》§5）。
 * 床位分配/释放全部走条件更新；住院状态机变更收敛于本服务与 InpAppService。
 */
@Service
@RequiredArgsConstructor
public class InpService {
    private final InpAdmissionMapper admissionMapper;
    private final InpBedMapper bedMapper;
    private final InpWardMapper wardMapper;
    private final InpTransferMapper transferMapper;
    private final InpDepositMapper depositMapper;
    private final BilChargeBillMapper billMapper;
    private final InpDailyFeeMapper dailyFeeMapper;
    private final PatientAppService patientAppService;
    private final BasedataAppService basedataAppService;
    private final InpAppService inpAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final org.springframework.beans.factory.ObjectProvider<com.his.modules.inp.spi.DischargeCheckHook> dischargeHooks;

    /** 入院登记（I-04）：无在院记录校验 + 床位条件分配 + 首笔押金，一个事务 */
    @Transactional
    public AdmissionResponse createAdmission(AdmissionCreateRequest req) {
        // 患者行锁串行化：并发登记同患者时后到者在锁上排队，预检必见前一笔在院记录（B6002）
        patientAppService.lockForAdmission(req.getPatientId());
        PatientDTO patient = patientAppService.requireActive(req.getPatientId());
        Long active = admissionMapper.countActiveByPatient(req.getPatientId());
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.B6002);
        }
        InpWard ward = wardMapper.selectById(req.getWardId());
        if (ward == null || ward.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "病区不存在或已停用");
        }
        if (!ward.getDeptId().equals(req.getDeptId())) {
            throw new BizException(ErrorCode.A0001, "科室与病区不匹配（病区归属科室 " + ward.getDeptId() + "）");
        }
        InpBed bedCheck = bedMapper.selectById(req.getBedId());
        if (bedCheck == null || !bedCheck.getWardId().equals(req.getWardId())) {
            throw new BizException(ErrorCode.A0001, "床位不属于所选病区");
        }
        // 先插入住院记录（status=10），再条件更新占床；占床失败整体回滚
        InpAdmission admission = new InpAdmission();
        admission.setAdmissionNo(idGenerator.next("ZY"));
        admission.setPatientId(req.getPatientId());
        admission.setDeptId(req.getDeptId());
        admission.setWardId(req.getWardId());
        admission.setBedId(req.getBedId());
        admission.setDoctorId(req.getDoctorId());
        admission.setAdmissionType(req.getAdmissionType());
        admission.setAdmissionTime(LocalDateTime.now());
        admission.setPlannedDiagnosis(req.getPlannedDiagnosis());
        admission.setDepositTotal(BigDecimal.ZERO);
        admission.setStatus(10);
        admissionMapper.insert(admission);

        int occupied = bedMapper.occupyBed(req.getBedId(), admission.getId());
        if (occupied == 0) {
            throw new BizException(ErrorCode.B6001);
        }
        // 首笔押金
        if (req.getDepositAmount() != null) {
            saveDeposit(admission.getId(), req.getDepositAmount(), req.getPayMethod());
            admission.setDepositTotal(req.getDepositAmount());
            admissionMapper.updateById(admission);
        }
        pltService.recordEvent("admission.created", admission.getAdmissionNo(),
                "{\"patientId\":" + req.getPatientId() + ",\"wardId\":" + req.getWardId() + "}");
        return detail(admission.getId());
    }

    /** 押金缴纳（I-08）：仅"在院"可缴 */
    @Transactional
    public BigDecimal addDeposit(Long admissionId, DepositRequest req) {
        InpAdmission admission = inpAppService.requireInHospital(admissionId);
        BigDecimal added = saveDeposit(admissionId, req.getAmount(), req.getPayMethod());
        // 原子递增（并发补押金不丢写，对齐退费行锁同级别的保障）
        admissionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InpAdmission>()
                .eq(InpAdmission::getId, admissionId)
                .setSql("deposit_total = deposit_total + {0}", added));
        return admission.getDepositTotal().add(added);
    }

    /** 退押金（七十轮）：仅已结算住院可退，上限 = 押金余额 - 结算账单额（对齐结算响应应退口径） */
    @Transactional
    public BigDecimal refundDeposit(Long admissionId, DepositRefundRequest req) {
        InpAdmission admission = inpAppService.requireAdmission(admissionId);
        if (admission.getStatus() == null || admission.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "住院未结算，不能退押金");
        }
        BigDecimal billTotal = BigDecimal.ZERO;
        BilChargeBill bill = billMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BilChargeBill>()
                .eq(BilChargeBill::getAdmissionId, admissionId).last("LIMIT 1"));
        if (bill != null && bill.getTotalAmount() != null) {
            billTotal = bill.getTotalAmount();
        }
        BigDecimal refundable = admission.getDepositTotal().subtract(billTotal);
        if (req.getAmount().compareTo(refundable) > 0) {
            throw new BizException(ErrorCode.A0001, "退押金超过应退金额（上限 " + refundable + "）");
        }
        saveDeposit(admissionId, req.getAmount().negate(), req.getPayMethod());
        admissionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InpAdmission>()
                .eq(InpAdmission::getId, admissionId)
                .setSql("deposit_total = deposit_total - {0}", req.getAmount()));
        return admission.getDepositTotal().subtract(req.getAmount());
    }

    /** 转科（I-07）：释放原床位 + 占用新床位 + 记录留痕，一个事务 */
    @Transactional
    public void transfer(Long admissionId, TransferRequest req) {
        InpAdmission admission = inpAppService.requireInHospital(admissionId);
        InpWard toWard = wardMapper.selectById(req.getToWardId());
        if (toWard == null || toWard.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "目标病区不存在或已停用");
        }
        InpBed toBed = bedMapper.selectById(req.getToBedId());
        if (toBed == null || !toBed.getWardId().equals(req.getToWardId())) {
            throw new BizException(ErrorCode.A0001, "目标床位不属于所选病区");
        }
        int occupy = bedMapper.occupyBed(req.getToBedId(), admissionId);
        if (occupy == 0) {
            throw new BizException(ErrorCode.B6005);
        }
        bedMapper.releaseBed(admission.getBedId(), admissionId);
        InpTransfer transfer = new InpTransfer();
        transfer.setAdmissionId(admissionId);
        transfer.setFromDeptId(admission.getDeptId());
        transfer.setToDeptId(toWard.getDeptId());
        transfer.setFromWardId(admission.getWardId());
        transfer.setToWardId(req.getToWardId());
        transfer.setTransferTime(LocalDateTime.now());
        transfer.setReason(req.getReason());
        transfer.setStatus(1);
        transferMapper.insert(transfer);

        admission.setDeptId(toWard.getDeptId());
        admission.setWardId(req.getToWardId());
        admission.setBedId(req.getToBedId());
        admissionMapper.updateById(admission);
        pltService.recordEvent("admission.transferred", admission.getAdmissionNo(),
                "{\"toWardId\":" + req.getToWardId() + "}");
    }

    /** 出院申请（I-11）：前置校验（未收费检查/长期医嘱）→ 出院未结 */
    @Transactional
    public void discharge(Long admissionId, DischargeRequest req) {
        InpAdmission admission = inpAppService.requireInHospital(admissionId);
        // 出院前置检查（依赖倒置：医嘱/检查模块各自实现钩子，《08》§2 依赖规则）
        for (com.his.modules.inp.spi.DischargeCheckHook hook : dischargeHooks) {
            String blocker = hook.checkBlocker(admissionId);
            if (blocker != null) {
                throw new BizException(ErrorCode.B6006, blocker);
            }
        }
        bedMapper.releaseBed(admission.getBedId(), admissionId);
        admission.setDischargeWay(req.getDischargeWay());
        admission.setDischargeDiagnosis(req.getDischargeDiagnosis());
        admission.setDischargeTime(LocalDateTime.now());
        // 无未结费用（当日入出等场景）直接闭环：否则无结算单可办，状态永久卡在 20（三十九轮 UI 走查实锤）
        Long unpaidFees = dailyFeeMapper.selectCount(new LambdaQueryWrapper<InpDailyFee>()
                .eq(InpDailyFee::getAdmissionId, admissionId)
                .eq(InpDailyFee::getChargeStatus, 0)
                .eq(InpDailyFee::getStatus, 1));
        boolean autoSettled = unpaidFees == null || unpaidFees == 0;
        admission.setStatus(autoSettled ? 30 : 20);
        admissionMapper.updateById(admission);
        pltService.recordEvent("admission.discharged", admission.getAdmissionNo(),
                "{\"way\":" + req.getDischargeWay() + ",\"autoSettled\":" + autoSettled + "}");
    }

    /** 一日清（I-09）：按日期分组的费用明细 */
    public List<FeeGroupResponse> dailyFees(Long admissionId) {
        List<InpDailyFee> fees = dailyFeeMapper.selectList(new LambdaQueryWrapper<InpDailyFee>()
                .eq(InpDailyFee::getAdmissionId, admissionId)
                .eq(InpDailyFee::getStatus, 1)
                .orderByAsc(InpDailyFee::getFeeDate).orderByAsc(InpDailyFee::getId));
        Map<LocalDate, List<InpDailyFee>> byDate = fees.stream()
                .collect(Collectors.groupingBy(InpDailyFee::getFeeDate, LinkedHashMap::new, Collectors.toList()));
        return byDate.entrySet().stream().map(e -> {
            FeeGroupResponse group = new FeeGroupResponse();
            group.setFeeDate(e.getKey());
            group.setItems(e.getValue().stream().map(f -> {
                FeeGroupResponse.FeeItem item = new FeeGroupResponse.FeeItem();
                item.setId(f.getId());
                item.setFeeType(f.getFeeType());
                item.setSourceType(f.getSourceType());
                item.setItemName(f.getItemName());
                item.setQuantity(f.getQuantity());
                item.setUnitPrice(f.getUnitPrice());
                item.setAmount(f.getAmount());
                return item;
            }).toList());
            group.setTotalAmount(e.getValue().stream().map(InpDailyFee::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            return group;
        }).toList();
    }

    /** 手工记账（I-10）：仅"在院" */
    @Transactional
    public Long addManualFee(Long admissionId, ManualFeeRequest req) {
        inpAppService.requireInHospital(admissionId);
        InpDailyFee fee = new InpDailyFee();
        fee.setAdmissionId(admissionId);
        fee.setFeeDate(LocalDate.now());
        fee.setFeeType(req.getFeeType());
        fee.setSourceType(4);
        fee.setSourceDetailId(0L);
        fee.setItemName(req.getItemName());
        fee.setQuantity(req.getQuantity());
        fee.setUnitPrice(req.getUnitPrice());
        fee.setAmount(req.getUnitPrice().multiply(req.getQuantity()).setScale(2, RoundingMode.HALF_UP));
        fee.setChargeStatus(0);
        fee.setStatus(1);
        dailyFeeMapper.insert(fee);
        return fee.getId();
    }

    /** 启用病区列表 */
    public List<InpWard> wards() {
        return wardMapper.selectList(new LambdaQueryWrapper<InpWard>()
                .eq(InpWard::getStatus, 1).orderByAsc(InpWard::getId));
    }

    /** 新增床位（I-02） */
    @Transactional
    public Long createBed(com.his.modules.inp.dto.BedCreateRequest req) {
        InpWard ward = wardMapper.selectById(req.getWardId());
        if (ward == null || ward.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "病区不存在或已停用");
        }
        Long dup = bedMapper.selectCount(new LambdaQueryWrapper<InpBed>()
                .eq(InpBed::getWardId, req.getWardId())
                .eq(InpBed::getBedNo, req.getBedNo()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.B5001, "床号已存在");
        }
        // 床位费必须选床位费类项目（类别8），否则每日记账类别错乱
        ChargeItemDTO bedFee = basedataAppService.getChargeItem(req.getChargeItemId());
        if (bedFee == null || bedFee.getCategory() == null || bedFee.getCategory() != 8) {
            throw new BizException(ErrorCode.A0001, "床位费必须选择类别为床位费的收费项目");
        }
        InpBed bed = new InpBed();
        bed.setWardId(req.getWardId());
        bed.setBedNo(req.getBedNo());
        bed.setBedStatus(1);
        bed.setChargeItemId(req.getChargeItemId());
        bed.setStatus(1);
        bedMapper.insert(bed);
        return bed.getId();
    }

    /** 床位状态调整（I-03）：占用中禁止直接改状态 */
    @Transactional
    public void updateBedStatus(Long bedId, Integer target) {
        InpBed bed = bedMapper.selectById(bedId);
        if (bed == null) {
            throw new BizException(ErrorCode.A0001, "床位不存在");
        }
        if (bed.getBedStatus() == 2) {
            throw new BizException(ErrorCode.B6001, "床位占用中，请先办理出院或转科");
        }
        bed.setBedStatus(target);
        bedMapper.updateById(bed);
    }

    /** 住院记录分页（I-05） */
    public PageResult<AdmissionResponse> page(AdmissionQuery query) {
        Page<InpAdmission> page = admissionMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<InpAdmission>()
                        .eq(query.getPatientId() != null, InpAdmission::getPatientId, query.getPatientId())
                        .eq(query.getDeptId() != null, InpAdmission::getDeptId, query.getDeptId())
                        .eq(query.getWardId() != null, InpAdmission::getWardId, query.getWardId())
                        .eq(query.getStatus() != null, InpAdmission::getStatus, query.getStatus())
                        .like(query.getAdmissionNo() != null && !query.getAdmissionNo().isBlank(),
                                InpAdmission::getAdmissionNo, LikeEscapeUtil.escape(query.getAdmissionNo()))
                        .orderByDesc(InpAdmission::getId));
        Map<Long, AdmissionResponse> assembled = toResponses(page.getRecords());
        PageResult<AdmissionResponse> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(page.getRecords().stream().map(r -> assembled.get(r.getId())).toList());
        return result;
    }

    public AdmissionResponse detail(Long admissionId) {
        InpAdmission admission = inpAppService.requireAdmission(admissionId);
        return toResponses(List.of(admission)).get(admission.getId());
    }

    /** 床位一览（N-06 护理工作台复用） */
    public List<BedVO> beds(Long wardId, Integer bedStatus) {
        List<InpBed> beds = bedMapper.selectList(new LambdaQueryWrapper<InpBed>()
                .eq(wardId != null, InpBed::getWardId, wardId)
                .eq(bedStatus != null, InpBed::getBedStatus, bedStatus)
                .orderByAsc(InpBed::getBedNo));
        Map<Long, InpWard> wardMap = wardMapper.selectList(null).stream()
                .collect(Collectors.toMap(InpWard::getId, w -> w));
        // 批量取数：原逐床 selectById 双重 N+1（住院+收费项目），379 床 ≈ 750 次单查 → 4 次批查
        List<Long> admissionIds = beds.stream().map(InpBed::getCurrentAdmissionId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, InpAdmission> admissionMap = admissionIds.isEmpty() ? Map.of()
                : admissionMapper.selectBatchIds(admissionIds).stream()
                        .collect(Collectors.toMap(InpAdmission::getId, a -> a));
        Map<Long, PatientDTO> patients = new LinkedHashMap<>();
        List<Long> pids = admissionMap.values().stream()
                .map(InpAdmission::getPatientId).distinct().toList();
        for (PatientDTO p : patientAppService.listByIds(pids)) {
            patients.put(p.getId(), p);
        }
        List<Long> chargeItemIds = beds.stream().map(InpBed::getChargeItemId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, ChargeItemDTO> chargeItems = basedataAppService.listChargeItemsByIds(chargeItemIds);
        return beds.stream().map(bed -> {
            BedVO vo = new BedVO();
            vo.setId(bed.getId());
            vo.setWardId(bed.getWardId());
            InpWard ward = wardMap.get(bed.getWardId());
            vo.setWardName(ward == null ? null : ward.getWardName());
            vo.setBedNo(bed.getBedNo());
            vo.setBedStatus(bed.getBedStatus());
            vo.setCurrentAdmissionId(bed.getCurrentAdmissionId());
            if (bed.getCurrentAdmissionId() != null) {
                InpAdmission admission = admissionMap.get(bed.getCurrentAdmissionId());
                if (admission != null) {
                    PatientDTO p = patients.get(admission.getPatientId());
                    vo.setPatientName(p == null ? null : p.getName());
                }
            }
            ChargeItemDTO item = bed.getChargeItemId() == null ? null : chargeItems.get(bed.getChargeItemId());
            vo.setBedFee(item == null ? null : item.getPrice());
            return vo;
        }).toList();
    }

    private BigDecimal saveDeposit(Long admissionId, BigDecimal amount, Integer payMethod) {
        InpDeposit deposit = new InpDeposit();
        deposit.setAdmissionId(admissionId);
        deposit.setAmount(amount);
        deposit.setPayMethod(payMethod);
        deposit.setPayTime(LocalDateTime.now());
        deposit.setOperatorId(CurrentUser.id());
        deposit.setStatus(1);
        depositMapper.insert(deposit);
        return amount;
    }

    private Map<Long, AdmissionResponse> toResponses(List<InpAdmission> admissions) {
        List<Long> patientIds = admissions.stream().map(InpAdmission::getPatientId).distinct().toList();
        Map<Long, PatientDTO> patientMap = patientAppService.listByIds(patientIds).stream()
                .collect(Collectors.toMap(PatientDTO::getId, p -> p));
        Map<Long, DepartmentDTO> deptMap = basedataAppService.departmentMap();
        Map<Long, InpWard> wardMap = wardMapper.selectList(null).stream()
                .collect(Collectors.toMap(InpWard::getId, w -> w));
        Map<Long, AdmissionResponse> result = new LinkedHashMap<>();
        for (InpAdmission admission : admissions) {
            AdmissionResponse resp = new AdmissionResponse();
            resp.setId(admission.getId());
            resp.setAdmissionNo(admission.getAdmissionNo());
            resp.setPatientId(admission.getPatientId());
            PatientDTO patient = patientMap.get(admission.getPatientId());
            if (patient != null) {
                resp.setPatientName(patient.getName());
                resp.setPatientNo(patient.getPatientNo());
            }
            resp.setDeptId(admission.getDeptId());
            DepartmentDTO dept = deptMap.get(admission.getDeptId());
            resp.setDeptName(dept == null ? null : dept.getDeptName());
            resp.setWardId(admission.getWardId());
            InpWard ward = wardMap.get(admission.getWardId());
            resp.setWardName(ward == null ? null : ward.getWardName());
            resp.setBedId(admission.getBedId());
            InpBed bed = bedMapper.selectById(admission.getBedId());
            resp.setBedNo(bed == null ? null : bed.getBedNo());
            resp.setDoctorId(admission.getDoctorId());
            resp.setAdmissionType(admission.getAdmissionType());
            resp.setAdmissionTime(admission.getAdmissionTime());
            resp.setPlannedDiagnosis(admission.getPlannedDiagnosis());
            resp.setDepositTotal(admission.getDepositTotal());
            resp.setDischargeWay(admission.getDischargeWay());
            resp.setDischargeDiagnosis(admission.getDischargeDiagnosis());
            resp.setDischargeTime(admission.getDischargeTime());
            resp.setStatus(admission.getStatus());
            result.put(admission.getId(), resp);
        }
        return result;
    }
}
