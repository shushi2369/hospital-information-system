package com.his.modules.nur.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.nur.dto.ScheduleRequest;
import com.his.modules.nur.dto.VitalSignRequest;
import com.his.modules.nur.entity.NurSchedule;
import com.his.modules.nur.entity.NurVitalSign;
import com.his.modules.nur.mapper.NurScheduleMapper;
import com.his.modules.nur.mapper.NurVitalSignMapper;
import com.his.modules.patient.app.PatientAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 护理服务（N-01~N-03）：排班、体征（范围校验）、体温单数据。
 */
@Service
@RequiredArgsConstructor
public class NurService {
    private final NurScheduleMapper scheduleMapper;
    private final NurVitalSignMapper vitalSignMapper;
    private final PatientAppService patientAppService;
    private final InpAppService inpAppService;

    /** 排班（N-01）：同护士同日唯一 */
    @Transactional
    public Long schedule(ScheduleRequest req) {
        Long dup = scheduleMapper.selectCount(new LambdaQueryWrapper<NurSchedule>()
                .eq(NurSchedule::getNurseId, req.getNurseId())
                .eq(NurSchedule::getShiftDate, req.getShiftDate()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.A0001, "该护士当日已有排班");
        }
        NurSchedule schedule = new NurSchedule();
        schedule.setNurseId(req.getNurseId());
        schedule.setWardId(req.getWardId());
        schedule.setShiftDate(req.getShiftDate());
        schedule.setShiftType(req.getShiftType());
        schedule.setStatus(1);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    /** 排班查询 */
    public List<NurSchedule> schedules(Long wardId, LocalDate shiftDate) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<NurSchedule>()
                .eq(wardId != null, NurSchedule::getWardId, wardId)
                .eq(shiftDate != null, NurSchedule::getShiftDate, shiftDate)
                .orderByAsc(NurSchedule::getShiftDate));
    }

    /** 体征录入（N-02）：范围校验（体温单口径） */
    @Transactional
    public Long addVitalSign(VitalSignRequest req) {
        InpAdmission admission = inpAppService.getAdmission(req.getAdmissionId());
        if (admission == null) {
            throw new BizException(ErrorCode.A0001, "住院记录不存在");
        }
        validateRange("体温", req.getTemperature(), 30, 42);
        validateRange("脉搏", req.getPulse(), 30, 250);
        validateRange("呼吸", req.getRespiration(), 5, 60);
        validateRange("收缩压", req.getBpHigh(), 40, 260);
        validateRange("舒张压", req.getBpLow(), 20, 180);
        if (req.getSpo2() != null) {
            validateRange("血氧", req.getSpo2(), 50, 100);
        }
        if (req.getPainScore() != null) {
            validateRange("疼痛评分", req.getPainScore(), 0, 10);
        }
        NurVitalSign vital = new NurVitalSign();
        vital.setAdmissionId(req.getAdmissionId());
        vital.setPatientId(admission.getPatientId());
        vital.setRecordTime(req.getRecordTime() == null ? LocalDateTime.now() : req.getRecordTime());
        vital.setTemperature(BigDecimal.valueOf(req.getTemperature()));
        vital.setPulse(req.getPulse());
        vital.setRespiration(req.getRespiration());
        vital.setBpHigh(req.getBpHigh());
        vital.setBpLow(req.getBpLow());
        vital.setSpo2(req.getSpo2());
        vital.setPainScore(req.getPainScore());
        vital.setNurseId(CurrentUser.id());
        vital.setStatus(1);
        vitalSignMapper.insert(vital);
        return vital.getId();
    }

    /** 体征查询（N-03，体温单数据源） */
    public List<NurVitalSign> vitalSigns(Long admissionId, LocalDateTime start, LocalDateTime end) {
        return vitalSignMapper.selectList(new LambdaQueryWrapper<NurVitalSign>()
                .eq(NurVitalSign::getAdmissionId, admissionId)
                .ge(start != null, NurVitalSign::getRecordTime, start)
                .le(end != null, NurVitalSign::getRecordTime, end)
                .orderByAsc(NurVitalSign::getRecordTime));
    }

    private void validateRange(String label, Object value, double min, double max) {
        double v = value instanceof BigDecimal bd ? bd.doubleValue() : ((Number) value).doubleValue();
        if (v < min || v > max) {
            throw new BizException(ErrorCode.A0001, label + "超出合理范围（" + min + "~" + max + "）");
        }
    }
}
