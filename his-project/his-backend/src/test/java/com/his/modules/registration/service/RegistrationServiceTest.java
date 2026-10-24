package com.his.modules.registration.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.basedata.app.DoctorDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.registration.dto.RegistrationCreateRequest;
import com.his.modules.registration.entity.RegRegistration;
import com.his.modules.registration.mapper.RegRegistrationMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** RegistrationService 单测：挂号 R1~R3 规则 + 专家号资质 + 退号状态机。 */
class RegistrationServiceTest extends UnitTestBase {

    private final RegRegistrationMapper registrationMapper = mock(RegRegistrationMapper.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final IdGenerator idGen = mock(IdGenerator.class);

    private RegistrationService svc() {
        return new RegistrationService(registrationMapper, patientAppService, basedataAppService, idGen);
    }

    private DoctorDTO doctor(int quota, int isExpert) {
        DoctorDTO d = new DoctorDTO();
        d.setId(2L);
        d.setDeptId(1L);
        d.setDoctorName("测试医生");
        d.setStatus(1);
        d.setDailyQuota(quota);
        d.setIsExpert(isExpert);
        d.setNormalFee(new BigDecimal("10.00"));
        d.setExpertFee(new BigDecimal("30.00"));
        return d;
    }

    private ChargeItemDTO charge(String price) {
        ChargeItemDTO c = new ChargeItemDTO();
        c.setStatus(1);
        c.setPrice(new BigDecimal(price));
        return c;
    }

    private RegistrationCreateRequest req(LocalDate date) {
        RegistrationCreateRequest r = new RegistrationCreateRequest();
        r.setPatientId(9L);
        r.setDoctorId(2L);
        r.setRegDate(date);
        r.setPeriod(1);
        r.setRegType(1);
        return r;
    }

    // ---------------- create 规则 ----------------

    @Test
    void create_pastDateRejected() {
        BizException e = assertThrows(BizException.class,
                () -> svc().create(req(LocalDate.now().minusDays(1))));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void create_inactiveDoctorRejected() {
        when(basedataAppService.lockDoctor(2L)).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> svc().create(req(LocalDate.now())));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void create_duplicateRejected() {
        when(basedataAppService.lockDoctor(2L)).thenReturn(doctor(50, 0));
        when(registrationMapper.countValidByPatient(any(), any(), any(), any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class,
                () -> svc().create(req(LocalDate.now())));
        assertEquals(ErrorCode.B1002, e.getErrorCode());
    }

    @Test
    void create_quotaExceededRejected() {
        when(basedataAppService.lockDoctor(2L)).thenReturn(doctor(10, 0));
        when(registrationMapper.countValidByPatient(any(), any(), any(), any())).thenReturn(0L);
        when(registrationMapper.countOccupied(any(), any(), any())).thenReturn(10L);
        BizException e = assertThrows(BizException.class,
                () -> svc().create(req(LocalDate.now())));
        assertEquals(ErrorCode.B1003, e.getErrorCode());
    }

    @Test
    void create_expertOnNonExpertDoctorRejected() {
        when(basedataAppService.lockDoctor(2L)).thenReturn(doctor(50, 0)); // 非专家
        when(registrationMapper.countValidByPatient(any(), any(), any(), any())).thenReturn(0L);
        when(registrationMapper.countOccupied(any(), any(), any())).thenReturn(0L);
        RegistrationCreateRequest r = req(LocalDate.now());
        r.setRegType(2); // 专家号
        BizException e = assertThrows(BizException.class, () -> svc().create(r));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void create_success_feeSnapshotAndQueueNo() {
        when(basedataAppService.lockDoctor(2L)).thenReturn(doctor(50, 0));
        when(registrationMapper.countValidByPatient(any(), any(), any(), any())).thenReturn(0L);
        when(registrationMapper.countOccupied(any(), any(), any())).thenReturn(3L);
        when(registrationMapper.selectMaxQueueNo(any(), any(), any())).thenReturn(7);
        when(basedataAppService.getChargeItemByCode(any())).thenReturn(charge("5.00"));
        when(idGen.next("GH")).thenReturn("GH-T-1");
        when(patientAppService.latestCardNo(9L)).thenReturn("K-1");
        var result = svc().create(req(LocalDate.now()));
        assertEquals(8, result.getQueueNo(), "排队号 = max+1");
        assertEquals(new BigDecimal("10.00"), result.getRegFee(), "号费快照取普通号价");
        assertEquals(new BigDecimal("15.00"), result.getTotalFee(), "合计 = 号费+诊查费");
        verify(registrationMapper).insert(any(RegRegistration.class));
    }

    // ---------------- cancel（R4） ----------------

    @Test
    void cancel_wrongStatusRejected() {
        RegRegistration reg = new RegRegistration();
        reg.setId(1L);
        reg.setStatus(30); // 已就诊
        when(registrationMapper.selectById(1L)).thenReturn(reg);
        BizException e = assertThrows(BizException.class, () -> svc().cancel(1L));
        assertEquals(ErrorCode.B1004, e.getErrorCode());
    }

    @Test
    void cancel_optimisticLockConflictThrows() {
        RegRegistration reg = new RegRegistration();
        reg.setId(1L);
        reg.setStatus(10);
        when(registrationMapper.selectById(1L)).thenReturn(reg);
        when(registrationMapper.updateById(any())).thenReturn(0); // 并发接诊抢先
        BizException e = assertThrows(BizException.class, () -> svc().cancel(1L));
        assertEquals(ErrorCode.B1004, e.getErrorCode());
    }

    @Test
    void cancel_success() {
        RegRegistration reg = new RegRegistration();
        reg.setId(1L);
        reg.setStatus(10);
        when(registrationMapper.selectById(1L)).thenReturn(reg);
        when(registrationMapper.updateById(any())).thenReturn(1);
        assertDoesNotThrow(() -> svc().cancel(1L));
        assertEquals(20, reg.getStatus());
    }
}
