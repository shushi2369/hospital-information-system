package com.his.modules.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.basedata.app.DepartmentDTO;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.registration.dto.RegistrationCreateRequest;
import com.his.modules.registration.dto.RegistrationCreateResult;
import com.his.modules.registration.dto.RegistrationQuery;
import com.his.modules.registration.dto.RegistrationResponse;
import com.his.modules.registration.entity.RegRegistration;
import com.his.modules.registration.mapper.RegRegistrationMapper;
import com.his.infrastructure.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 挂号服务（R-01~R-05，规则《05》R1~R6）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final RegRegistrationMapper registrationMapper;
    private final PatientAppService patientAppService;
    private final BasedataAppService basedataAppService;
    private final IdGenerator idGenerator;

    /** 诊查费收费项目编码（配置化，演示数据为 ITEM001/ITEM002） */
    @Value("${his.registration.consult-item-normal:ITEM001}")
    private String consultItemNormal;
    @Value("${his.registration.consult-item-expert:ITEM002}")
    private String consultItemExpert;

    @Transactional
    public RegistrationCreateResult create(RegistrationCreateRequest req) {
        if (req.getRegDate().isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.A0001, "不能挂过去日期的号");
        }
        patientAppService.requireActive(req.getPatientId());
        // R2：事务内锁定医生行后再计数，防并发超发
        DoctorDTO doctor = basedataAppService.lockDoctor(req.getDoctorId());
        if (doctor == null || doctor.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "医生不存在或已停用");
        }
        // R1：防重复挂号（应用层校验而非唯一索引，保证退号后可重新挂号）
        Long dup = registrationMapper.countValidByPatient(
                req.getPatientId(), req.getDoctorId(), req.getRegDate(), req.getPeriod());
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.B1002);
        }
        // R2：号源（已挂号+已就诊占号，退号/过号释放）
        Long occupied = registrationMapper.countOccupied(
                req.getDoctorId(), req.getRegDate(), req.getPeriod());
        if (occupied != null && occupied >= doctor.getDailyQuota()) {
            throw new BizException(ErrorCode.B1003);
        }
        // R3：排队号（含退号号段不复用）
        Integer maxQueue = registrationMapper.selectMaxQueueNo(
                req.getDoctorId(), req.getRegDate(), req.getPeriod());
        int queueNo = (maxQueue == null ? 0 : maxQueue) + 1;

        // 号费快照 + 诊查费快照
        boolean expert = req.getRegType() == 2;
        BigDecimal regFee = expert ? doctor.getExpertFee() : doctor.getNormalFee();
        ChargeItemDTO consult = basedataAppService.getChargeItemByCode(expert ? consultItemExpert : consultItemNormal);
        if (consult == null || consult.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "诊查费收费项目未配置，请联系管理员");
        }

        RegRegistration reg = new RegRegistration();
        reg.setRegNo(idGenerator.next("GH"));
        reg.setPatientId(req.getPatientId());
        reg.setCardNo(patientAppService.latestCardNo(req.getPatientId()));
        reg.setDeptId(doctor.getDeptId());
        reg.setDoctorId(doctor.getId());
        reg.setRegDate(req.getRegDate());
        reg.setPeriod(req.getPeriod());
        reg.setRegType(req.getRegType());
        reg.setRegFee(regFee);
        reg.setConsultationFee(consult.getPrice());
        reg.setQueueNo(queueNo);
        reg.setChargeStatus(0);
        reg.setStatus(10);
        registrationMapper.insert(reg);

        return new RegistrationCreateResult(reg.getRegNo(), queueNo, regFee,
                consult.getPrice(), regFee.add(consult.getPrice()));
    }

    /** 退号（R4：仅已挂号可退；不自动退费，已收费由退费流程处理） */
    @Transactional
    public void cancel(Long id) {
        RegRegistration reg = requireRegistration(id);
        if (reg.getStatus() != 10) {
            throw new BizException(ErrorCode.B1004);
        }
        reg.setStatus(20);
        registrationMapper.updateById(reg);
    }

    public PageResult<RegistrationResponse> page(RegistrationQuery query) {
        Page<RegRegistration> page = registrationMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<RegRegistration>()
                        .eq(query.getPatientId() != null, RegRegistration::getPatientId, query.getPatientId())
                        .eq(query.getDoctorId() != null, RegRegistration::getDoctorId, query.getDoctorId())
                        .eq(query.getDeptId() != null, RegRegistration::getDeptId, query.getDeptId())
                        .eq(query.getRegDate() != null, RegRegistration::getRegDate, query.getRegDate())
                        .eq(query.getStatus() != null, RegRegistration::getStatus, query.getStatus())
                        .orderByDesc(RegRegistration::getId));
        Map<Long, RegistrationResponse> assembled = toResponses(page.getRecords());
        PageResult<RegistrationResponse> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(page.getRecords().stream().map(r -> assembled.get(r.getId())).toList());
        return result;
    }

    /** 候诊队列（R-04：按医生+日期+时段，queue_no 升序） */
    public List<RegistrationResponse> queue(Long doctorId, LocalDate regDate, Integer period) {
        return toResponses(registrationMapper.selectList(new LambdaQueryWrapper<RegRegistration>()
                        .eq(RegRegistration::getDoctorId, doctorId)
                        .eq(RegRegistration::getRegDate, regDate)
                        .eq(period != null, RegRegistration::getPeriod, period)
                        .eq(RegRegistration::getStatus, 10)
                        .orderByAsc(RegRegistration::getQueueNo)))
                .values().stream()
                .sorted(java.util.Comparator.comparing(RegistrationResponse::getQueueNo))
                .toList();
    }

    public RegistrationResponse detail(Long id) {
        return toResponse(requireRegistration(id));
    }

    private RegRegistration requireRegistration(Long id) {
        RegRegistration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BizException(ErrorCode.B1005);
        }
        return reg;
    }

    private RegistrationResponse toResponse(RegRegistration reg) {
        return toResponses(List.of(reg)).get(reg.getId());
    }

    /** 批量组装挂号响应（一次患者批量 + 字典 Map，避免分页 N+1） */
    private Map<Long, RegistrationResponse> toResponses(List<RegRegistration> regs) {
        List<Long> patientIds = regs.stream().map(RegRegistration::getPatientId).distinct().toList();
        Map<Long, PatientDTO> patientMap = patientAppService.listByIds(patientIds).stream()
                .collect(Collectors.toMap(PatientDTO::getId, p -> p));
        Map<Long, DoctorDTO> doctorMap = basedataAppService.doctorMap();
        Map<Long, DepartmentDTO> deptMap = basedataAppService.departmentMap();
        Map<Long, RegistrationResponse> result = new LinkedHashMap<>();
        for (RegRegistration reg : regs) {
            RegistrationResponse resp = new RegistrationResponse();
            resp.setId(reg.getId());
            resp.setRegNo(reg.getRegNo());
            resp.setPatientId(reg.getPatientId());
            resp.setCardNo(reg.getCardNo());
            resp.setDeptId(reg.getDeptId());
            resp.setDoctorId(reg.getDoctorId());
            resp.setRegDate(reg.getRegDate());
            resp.setPeriod(reg.getPeriod());
            resp.setRegType(reg.getRegType());
            resp.setRegFee(reg.getRegFee());
            resp.setConsultationFee(reg.getConsultationFee());
            resp.setQueueNo(reg.getQueueNo());
            resp.setStatus(reg.getStatus());
            resp.setChargeStatus(reg.getChargeStatus());
            resp.setCreatedAt(reg.getCreatedAt());
            PatientDTO patient = patientMap.get(reg.getPatientId());
            if (patient != null) {
                resp.setPatientName(patient.getName());
                resp.setPatientNo(patient.getPatientNo());
            }
            DoctorDTO doctor = doctorMap.get(reg.getDoctorId());
            if (doctor != null) {
                resp.setDoctorName(doctor.getDoctorName());
            }
            DepartmentDTO dept = deptMap.get(reg.getDeptId());
            if (dept != null) {
                resp.setDeptName(dept.getDeptName());
            }
            result.put(reg.getId(), resp);
        }
        return result;
    }
}
