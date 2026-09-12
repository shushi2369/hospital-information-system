package com.his.modules.registration.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.registration.entity.RegRegistration;
import com.his.modules.registration.mapper.RegRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 挂号就诊跨模块应用服务（医生工作站接诊、收费模块统一经此读写 reg_ 表）。
 */
@Service
@RequiredArgsConstructor
public class RegistrationAppService {
    private final RegRegistrationMapper registrationMapper;
    private final PatientAppService patientAppService;
    private final BasedataAppService basedataAppService;

    public RegistrationDTO getById(Long registrationId) {
        RegRegistration reg = registrationMapper.selectById(registrationId);
        return reg == null ? null : toDTO(reg);
    }

    /**
     * 接诊联动：挂号单 10→30（《05》状态机），非已挂号状态抛 B1004；
     * 乐观锁更新，重复接诊由调用方按幂等处理。
     */
    @Transactional
    public RegistrationDTO markVisited(Long registrationId) {
        RegRegistration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BizException(ErrorCode.B1005);
        }
        if (reg.getStatus() != 10) {
            throw new BizException(ErrorCode.B1004, "该挂号单已退号或已就诊，不能重复接诊");
        }
        reg.setStatus(30);
        registrationMapper.updateById(reg);
        return toDTO(reg);
    }

    /** 医生当日候诊列表（status=10，排队号升序） */
    public List<RegistrationDTO> listQueueByDoctor(Long doctorId, LocalDate regDate) {
        return registrationMapper.selectList(new LambdaQueryWrapper<RegRegistration>()
                        .eq(RegRegistration::getDoctorId, doctorId)
                        .eq(RegRegistration::getRegDate, regDate)
                        .eq(RegRegistration::getStatus, 10)
                        .orderByAsc(RegRegistration::getQueueNo))
                .stream().map(this::toDTO).toList();
    }

    /** 收费联动：挂号费+诊查费标记已收费（charge_status 0→1） */
    @Transactional
    public void markCharged(Long registrationId) {
        RegRegistration reg = registrationMapper.selectById(registrationId);
        if (reg == null || reg.getChargeStatus() == 1) {
            return;
        }
        reg.setChargeStatus(1);
        registrationMapper.updateById(reg);
    }

    /** 已就诊且挂号费未收（收费窗口待缴费列表来源之一） */
    public List<RegistrationDTO> listVisitedUnpaid() {
        return registrationMapper.selectList(new LambdaQueryWrapper<RegRegistration>()
                        .eq(RegRegistration::getStatus, 30)
                        .eq(RegRegistration::getChargeStatus, 0)
                        .orderByDesc(RegRegistration::getId))
                .stream().map(this::toDTO).limit(100).toList();
    }

    /** 退挂号费联动：未就诊挂号单置已退号（《05》R5/R9，非已挂号状态静默跳过） */
    @Transactional
    public void cancelForRefund(Long registrationId) {
        RegRegistration reg = registrationMapper.selectById(registrationId);
        if (reg == null || reg.getStatus() != 10) {
            return;
        }
        reg.setStatus(20);
        registrationMapper.updateById(reg);
    }

    // ---------------- 只读统计（报表模块 T-01） ----------------

    /** 日挂号量（不含已退号） */
    public List<DailyStatDTO> dailyRegistrations(LocalDate start, LocalDate end) {
        return registrationMapper.dailyRegistrations(start, end);
    }

    /** 科室挂号排名 */
    public List<DailyStatDTO> deptRegistrationRanking(LocalDate start, LocalDate end) {
        return registrationMapper.deptRegistrationRanking(start, end);
    }

    private RegistrationDTO toDTO(RegRegistration reg) {
        RegistrationDTO dto = new RegistrationDTO();
        dto.setId(reg.getId());
        dto.setRegNo(reg.getRegNo());
        dto.setPatientId(reg.getPatientId());
        dto.setCardNo(reg.getCardNo());
        dto.setDeptId(reg.getDeptId());
        dto.setDoctorId(reg.getDoctorId());
        dto.setRegDate(reg.getRegDate());
        dto.setPeriod(reg.getPeriod());
        dto.setRegType(reg.getRegType());
        dto.setRegFee(reg.getRegFee());
        dto.setConsultationFee(reg.getConsultationFee());
        dto.setQueueNo(reg.getQueueNo());
        dto.setStatus(reg.getStatus());
        dto.setChargeStatus(reg.getChargeStatus());
        PatientDTO patient = patientAppService.getById(reg.getPatientId());
        if (patient != null) {
            dto.setPatientName(patient.getName());
            dto.setPatientNo(patient.getPatientNo());
        }
        DoctorDTO doctor = basedataAppService.getDoctor(reg.getDoctorId());
        if (doctor != null) {
            dto.setDoctorName(doctor.getDoctorName());
        }
        var dept = basedataAppService.getDepartment(reg.getDeptId());
        if (dept != null) {
            dto.setDeptName(dept.getDeptName());
        }
        return dto;
    }
}
