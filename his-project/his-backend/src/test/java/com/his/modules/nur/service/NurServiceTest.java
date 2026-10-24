package com.his.modules.nur.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.nur.dto.ScheduleRequest;
import com.his.modules.nur.dto.VitalSignRequest;
import com.his.modules.nur.entity.NurSchedule;
import com.his.modules.nur.mapper.NurScheduleMapper;
import com.his.modules.nur.mapper.NurVitalSignMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.system.app.SystemAppService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** NurService 单测：排班护士身份门禁 + 体征范围校验（体温单口径）+ 事务尾在院复核。 */
class NurServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, NurSchedule.class);
    }

    private final SystemAppService systemAppService = mock(SystemAppService.class);
    private final NurScheduleMapper scheduleMapper = mock(NurScheduleMapper.class);
    private final NurVitalSignMapper vitalSignMapper = mock(NurVitalSignMapper.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final InpAppService inpAppService = mock(InpAppService.class);

    private NurService svc() {
        return new NurService(systemAppService, scheduleMapper, vitalSignMapper,
                patientAppService, inpAppService);
    }

    private InpAdmission admission() {
        InpAdmission a = new InpAdmission();
        a.setId(900L);
        a.setPatientId(11L);
        return a;
    }

    private VitalSignRequest vitalReq(Double temp, Integer pulse, Integer resp,
                                      Integer bpHigh, Integer bpLow) {
        VitalSignRequest r = new VitalSignRequest();
        r.setAdmissionId(900L);
        r.setTemperature(temp);
        r.setPulse(pulse);
        r.setRespiration(resp);
        r.setBpHigh(bpHigh);
        r.setBpLow(bpLow);
        return r;
    }

    @Test
    void schedule_nonNurse_throws() {
        when(systemAppService.isNurse(66L)).thenReturn(false);
        ScheduleRequest req = new ScheduleRequest();
        req.setNurseId(66L);
        BizException e = assertThrows(BizException.class, () -> svc().schedule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void schedule_duplicateDay_throws() {
        when(systemAppService.isNurse(66L)).thenReturn(true);
        when(scheduleMapper.selectCount(any())).thenReturn(1L);
        ScheduleRequest req = new ScheduleRequest();
        req.setNurseId(66L);
        req.setShiftDate(LocalDate.now());
        BizException e = assertThrows(BizException.class, () -> svc().schedule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void schedule_happy_defaultsEnabled() {
        when(systemAppService.isNurse(66L)).thenReturn(true);
        when(scheduleMapper.selectCount(any())).thenReturn(0L);

        ScheduleRequest req = new ScheduleRequest();
        req.setNurseId(66L);
        req.setWardId(3L);
        req.setShiftDate(LocalDate.now());
        req.setShiftType(1);

        svc().schedule(req);

        ArgumentCaptor<NurSchedule> captor = ArgumentCaptor.forClass(NurSchedule.class);
        verify(scheduleMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals(3L, captor.getValue().getWardId());
    }

    @Test
    void schedule_raceDuplicateKey_throws() {
        when(systemAppService.isNurse(66L)).thenReturn(true);
        when(scheduleMapper.selectCount(any())).thenReturn(0L);
        when(scheduleMapper.insert(any(NurSchedule.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));

        ScheduleRequest req = new ScheduleRequest();
        req.setNurseId(66L);
        BizException e = assertThrows(BizException.class, () -> svc().schedule(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void addVitalSign_temperatureOutOfRange_throws() {
        when(inpAppService.requireInHospital(900L)).thenReturn(admission());
        BizException e = assertThrows(BizException.class,
                () -> svc().addVitalSign(vitalReq(50.0, 80, 18, 120, 80)));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
        assertTrue(e.getMessage().contains("体温"));
    }

    @Test
    void addVitalSign_systoleMustExceedDiastole_throws() {
        when(inpAppService.requireInHospital(900L)).thenReturn(admission());
        BizException e = assertThrows(BizException.class,
                () -> svc().addVitalSign(vitalReq(36.5, 80, 18, 80, 80)));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void addVitalSign_futureRecordTime_throws() {
        when(inpAppService.requireInHospital(900L)).thenReturn(admission());
        VitalSignRequest req = vitalReq(36.5, 80, 18, 120, 80);
        req.setRecordTime(LocalDateTime.now().plusHours(2));
        BizException e = assertThrows(BizException.class, () -> svc().addVitalSign(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void addVitalSign_spo2AndPainRangeChecked() {
        when(inpAppService.requireInHospital(900L)).thenReturn(admission());
        VitalSignRequest req = vitalReq(36.5, 80, 18, 120, 80);
        req.setPainScore(11);
        BizException e = assertThrows(BizException.class, () -> svc().addVitalSign(req));
        assertTrue(e.getMessage().contains("疼痛评分"));

        VitalSignRequest req2 = vitalReq(36.5, 80, 18, 120, 80);
        req2.setSpo2(30);
        BizException e2 = assertThrows(BizException.class, () -> svc().addVitalSign(req2));
        assertTrue(e2.getMessage().contains("血氧"));
    }

    @Test
    void addVitalSign_happy_copiesPatientAndDefaultsNow() {
        when(inpAppService.requireInHospital(900L)).thenReturn(admission());
        when(inpAppService.touchInHospital(900L)).thenReturn(true);

        // 返回值由 MyBatis 回填主键，单测 mock 不回填；仅断言落库实体字段
        svc().addVitalSign(vitalReq(36.5, 80, 18, 120, 80));

        ArgumentCaptor<com.his.modules.nur.entity.NurVitalSign> captor =
                ArgumentCaptor.forClass(com.his.modules.nur.entity.NurVitalSign.class);
        verify(vitalSignMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getPatientId());
        assertEquals(0, new BigDecimal("36.5").compareTo(captor.getValue().getTemperature()));
        assertNotNull(captor.getValue().getRecordTime());
        assertEquals(1L, captor.getValue().getNurseId());
    }

    @Test
    void addVitalSign_dischargedAtTail_throws() {
        // 事务尾在院复核：requireInHospital 通过后、落库前并发出院 → 体征必须回滚
        when(inpAppService.requireInHospital(900L)).thenReturn(admission());
        when(inpAppService.touchInHospital(900L)).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> svc().addVitalSign(vitalReq(36.5, 80, 18, 120, 80)));
        assertEquals(ErrorCode.B6003, e.getErrorCode());
    }
}
