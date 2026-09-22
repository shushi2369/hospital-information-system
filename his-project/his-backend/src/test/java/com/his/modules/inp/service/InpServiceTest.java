package com.his.modules.inp.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.dto.AdmissionCreateRequest;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.inp.entity.InpBed;
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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** InpService 入院登记单测：患者行锁顺序（三十一轮修复）+ 病区/床位校验 + 占床失败。 */
class InpServiceTest extends UnitTestBase {

    private final InpAdmissionMapper admissionMapper = mock(InpAdmissionMapper.class);
    private final InpBedMapper bedMapper = mock(InpBedMapper.class);
    private final InpWardMapper wardMapper = mock(InpWardMapper.class);
    private final InpTransferMapper transferMapper = mock(InpTransferMapper.class);
    private final InpDepositMapper depositMapper = mock(InpDepositMapper.class);
    private final InpDailyFeeMapper dailyFeeMapper = mock(InpDailyFeeMapper.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<com.his.modules.inp.spi.DischargeCheckHook> dischargeHooks =
            (ObjectProvider<com.his.modules.inp.spi.DischargeCheckHook>)
                    mock(ObjectProvider.class);

    private final InpService service = new InpService(
            admissionMapper, bedMapper, wardMapper, transferMapper, depositMapper,
            dailyFeeMapper, patientAppService, basedataAppService, inpAppService,
            pltService, idGenerator, dischargeHooks);

    private PatientDTO patient() {
        PatientDTO p = new PatientDTO();
        p.setId(5L);
        p.setName("测试患者");
        p.setStatus(1);
        return p;
    }

    private AdmissionCreateRequest req() {
        AdmissionCreateRequest r = new AdmissionCreateRequest();
        r.setPatientId(5L);
        r.setDeptId(1L);
        r.setWardId(1L);
        r.setBedId(1L);
        r.setDoctorId(2L);
        r.setAdmissionType(1);
        r.setPlannedDiagnosis("肺炎待查");
        return r;
    }

    private InpWard ward(Long deptId) {
        InpWard w = new InpWard();
        w.setId(1L);
        w.setDeptId(deptId);
        w.setStatus(1);
        return w;
    }

    private InpBed bed(Long wardId) {
        InpBed b = new InpBed();
        b.setId(1L);
        b.setWardId(wardId);
        b.setBedStatus(1);
        b.setStatus(1);
        return b;
    }

    @Test
    void createAdmission_locksPatientBeforeActiveCheck() {
        when(patientAppService.requireActive(5L)).thenReturn(patient());
        when(admissionMapper.countActiveByPatient(5L)).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.createAdmission(req()));
        assertEquals(ErrorCode.B6002, e.getErrorCode());
        // 行锁必须先于在院预检，才能串行化同患者并发登记（三十一轮修复语义）
        InOrder inOrder = inOrder(patientAppService, admissionMapper);
        inOrder.verify(patientAppService).lockForAdmission(5L);
        inOrder.verify(admissionMapper).countActiveByPatient(5L);
    }

    @Test
    void createAdmission_wardDeptMismatchRejected() {
        when(patientAppService.requireActive(5L)).thenReturn(patient());
        when(admissionMapper.countActiveByPatient(5L)).thenReturn(0L);
        when(wardMapper.selectById(1L)).thenReturn(ward(2L)); // 病区归属科室 2 ≠ 申请科室 1

        BizException e = assertThrows(BizException.class, () -> service.createAdmission(req()));
        assertTrue(e.getMessage().contains("不匹配"));
        verify(admissionMapper, never()).insert(any(InpAdmission.class));
    }

    @Test
    void createAdmission_bedNotInWardRejected() {
        when(patientAppService.requireActive(5L)).thenReturn(patient());
        when(admissionMapper.countActiveByPatient(5L)).thenReturn(0L);
        when(wardMapper.selectById(1L)).thenReturn(ward(1L));
        when(bedMapper.selectById(1L)).thenReturn(bed(9L)); // 床位属于病区 9

        BizException e = assertThrows(BizException.class, () -> service.createAdmission(req()));
        assertTrue(e.getMessage().contains("床位不属于所选病区"));
        verify(admissionMapper, never()).insert(any(InpAdmission.class));
    }

    @Test
    void createAdmission_occupyBedFailsRejected() {
        when(patientAppService.requireActive(5L)).thenReturn(patient());
        when(admissionMapper.countActiveByPatient(5L)).thenReturn(0L);
        when(wardMapper.selectById(1L)).thenReturn(ward(1L));
        when(bedMapper.selectById(1L)).thenReturn(bed(1L));
        when(bedMapper.occupyBed(any(), any())).thenReturn(0); // 床位被并发抢占

        BizException e = assertThrows(BizException.class, () -> service.createAdmission(req()));
        assertEquals(ErrorCode.B6001, e.getErrorCode());
        verify(pltService, never()).recordEvent(any(), any(), any());
    }
}
