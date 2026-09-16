package com.his.modules.inp.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.entity.InpDailyFee;
import com.his.modules.inp.mapper.InpAdmissionMapper;
import com.his.modules.inp.mapper.InpDailyFeeMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import lombok.Getter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 住院跨模块应用服务：医嘱/护理/病案/医保模块统一经此读写住院主记录与一日清。
 * 状态机（《10》§2.1）的变更全部收敛到本服务。
 */
@Service
@RequiredArgsConstructor
public class InpAppService {
    private final InpAdmissionMapper admissionMapper;
    private final InpDailyFeeMapper dailyFeeMapper;
    private final PatientAppService patientAppService;

    public InpAdmission getAdmission(Long admissionId) {
        return admissionMapper.selectById(admissionId);
    }

    public InpAdmission requireAdmission(Long admissionId) {
        InpAdmission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BizException(ErrorCode.A0001, "住院记录不存在");
        }
        return admission;
    }

    /** 住院视图 DTO 装配（供跨模块展示） */
    public AdmissionView getAdmissionView(Long admissionId) {
        InpAdmission admission = requireAdmission(admissionId);
        AdmissionView view = new AdmissionView();
        view.setAdmission(admission);
        PatientDTO patient = patientAppService.getById(admission.getPatientId());
        view.setPatientName(patient == null ? null : patient.getName());
        view.setPatientNo(patient == null ? null : patient.getPatientNo());
        return view;
    }

    /** 未结算一日清费用（出院结算取数，跨模块返回 DTO） */
    public List<DailyFeeDTO> listUnpaidDailyFees(Long admissionId) {
        return dailyFeeMapper.selectList(new LambdaQueryWrapper<InpDailyFee>()
                .eq(InpDailyFee::getAdmissionId, admissionId)
                .eq(InpDailyFee::getChargeStatus, 0)
                .eq(InpDailyFee::getStatus, 1)
                .orderByAsc(InpDailyFee::getFeeDate))
                .stream().map(f -> {
                    DailyFeeDTO dto = new DailyFeeDTO();
                    dto.setId(f.getId());
                    dto.setFeeDate(f.getFeeDate());
                    dto.setFeeType(f.getFeeType());
                    dto.setSourceType(f.getSourceType());
                    dto.setSourceDetailId(f.getSourceDetailId());
                    dto.setItemName(f.getItemName());
                    dto.setQuantity(f.getQuantity());
                    dto.setUnitPrice(f.getUnitPrice());
                    dto.setAmount(f.getAmount());
                    return dto;
                }).toList();
    }

    /** 结算联动：一日清标记已结算 */
    @Transactional
    public void markDailyFeesSettled(Long admissionId) {
        dailyFeeMapper.update(null, new LambdaUpdateWrapper<InpDailyFee>()
                .eq(InpDailyFee::getAdmissionId, admissionId)
                .eq(InpDailyFee::getChargeStatus, 0)
                .set(InpDailyFee::getChargeStatus, 1)
                .set(InpDailyFee::getUpdatedAt, java.time.LocalDateTime.now()));
    }

    /** 结算联动：住院 20 出院未结 → 30 已结算 */
    @Transactional
    public void markSettled(Long admissionId) {
        InpAdmission admission = requireAdmission(admissionId);
        if (admission.getStatus() != 20) {
            throw new BizException(ErrorCode.B6003, "住院非出院未结状态，不能结算");
        }
        admission.setStatus(30);
        admissionMapper.updateById(admission);
    }

    /** 医嘱执行计费（执行即记账，source_type=2 医嘱执行） */
    @Transactional
    public Long addExecFee(Long admissionId, Integer feeType, String itemName,
                           java.math.BigDecimal quantity, java.math.BigDecimal unitPrice,
                           Long sourceDetailId) {
        InpDailyFee fee = new InpDailyFee();
        fee.setAdmissionId(admissionId);
        fee.setFeeDate(java.time.LocalDate.now());
        fee.setFeeType(feeType);
        fee.setSourceType(2);
        fee.setSourceDetailId(sourceDetailId);
        fee.setItemName(itemName);
        fee.setQuantity(quantity);
        fee.setUnitPrice(unitPrice);
        fee.setAmount(unitPrice.multiply(quantity).setScale(2, java.math.RoundingMode.HALF_UP));
        fee.setChargeStatus(0);
        fee.setStatus(1);
        dailyFeeMapper.insert(fee);
        return fee.getId();
    }

    /** 在院校验（医嘱/护理模块经此校验操作合法性） */
    public InpAdmission requireInHospital(Long admissionId) {
        InpAdmission admission = requireAdmission(admissionId);
        if (admission.getStatus() != 10) {
            throw new BizException(ErrorCode.B6003);
        }
        return admission;
    }

    /** 按状态集合查住院（病案惰性补建用） */
    public List<InpAdmission> listAdmissionsByStatuses(List<Integer> statuses) {
        return admissionMapper.selectList(new LambdaQueryWrapper<InpAdmission>()
                .in(InpAdmission::getStatus, statuses)
                .orderByDesc(InpAdmission::getId)
                .last("LIMIT 500"));
    }

    /** 在院患者视图（护理工作台用） */
    public List<InpAdmission> listInHospitalByWard(Long wardId) {
        return admissionMapper.selectList(new LambdaQueryWrapper<InpAdmission>()
                .eq(wardId != null, InpAdmission::getWardId, wardId)
                .eq(InpAdmission::getStatus, 10)
                .orderByAsc(InpAdmission::getBedId));
    }

    /** 视图载体（inp 实体 + 患者解析） */
    @Getter
    @Setter
    public static class AdmissionView {
        private InpAdmission admission;
        private String patientName;
        private String patientNo;
    }
}
