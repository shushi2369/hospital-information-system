package com.his.modules.clinic.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.clinic.entity.CliPrescription;
import com.his.modules.clinic.entity.CliVisit;
import com.his.modules.clinic.mapper.CliDiagnosisMapper;
import com.his.modules.clinic.mapper.CliExamApplicationMapper;
import com.his.modules.clinic.mapper.CliMedicalOrderMapper;
import com.his.modules.clinic.mapper.CliPrescriptionItemMapper;
import com.his.modules.clinic.mapper.CliPrescriptionMapper;
import com.his.modules.clinic.mapper.CliVisitMapper;
import com.his.modules.clinic.dto.PrescriptionVoidRequest;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.pub.service.PubService;
import com.his.modules.registration.app.RegistrationAppService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ClinicService 单测：就诊状态门禁（R15 只读）+ 处方作废状态机（113/126 轮修复固化）。 */
class ClinicServiceTest extends UnitTestBase {

    @org.junit.jupiter.api.BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, CliPrescription.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, CliVisit.class);
    }


    private final CliVisitMapper visitMapper = mock(CliVisitMapper.class);
    private final CliDiagnosisMapper diagnosisMapper = mock(CliDiagnosisMapper.class);
    private final PubService pubService = mock(PubService.class);
    private final CliMedicalOrderMapper orderMapper = mock(CliMedicalOrderMapper.class);
    private final CliPrescriptionMapper prescriptionMapper = mock(CliPrescriptionMapper.class);
    private final CliPrescriptionItemMapper prescriptionItemMapper = mock(CliPrescriptionItemMapper.class);
    private final CliExamApplicationMapper examApplicationMapper = mock(CliExamApplicationMapper.class);
    private final RegistrationAppService registrationAppService = mock(RegistrationAppService.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private ClinicService svc() {
        return new ClinicService(visitMapper, diagnosisMapper, pubService, orderMapper,
                prescriptionMapper, prescriptionItemMapper, examApplicationMapper,
                registrationAppService, patientAppService, basedataAppService, idGenerator);
    }

    private CliVisit visit(Long id, int status, Long doctorId) {
        CliVisit v = new CliVisit();
        v.setId(id);
        v.setRegistrationId(100L);
        v.setDoctorId(doctorId);
        v.setStatus(status);
        return v;
    }

    private CliPrescription rx(Long id, int status, int chargeStatus, Long visitId) {
        CliPrescription p = new CliPrescription();
        p.setId(id);
        p.setRxNo("RX-T-" + id);
        p.setVisitId(visitId);
        p.setStatus(status);
        p.setChargeStatus(chargeStatus);
        return p;
    }

    // ---------------- requireVisitInProgress 三态（R15 只读） ----------------


    @Test
    void voidPrescription_chargedRxRejected() {
        when(visitMapper.selectById(1L)).thenReturn(visit(1L, 20, 1L));
        when(prescriptionMapper.selectById(9L)).thenReturn(rx(9L, 20, 1, 1L)); // 已收费
        BizException e = assertThrows(BizException.class,
                () -> svc().voidPrescription(9L, new PrescriptionVoidRequest()));
        assertEquals(ErrorCode.B2006, e.getErrorCode());
    }

    @Test
    void voidPrescription_dispensedRxRejected() {
        when(visitMapper.selectById(1L)).thenReturn(visit(1L, 20, 1L));
        when(prescriptionMapper.selectById(9L)).thenReturn(rx(9L, 30, 1, 1L)); // 已发药
        BizException e = assertThrows(BizException.class,
                () -> svc().voidPrescription(9L, new PrescriptionVoidRequest()));
        assertEquals(ErrorCode.B2006, e.getErrorCode());
    }

    @Test
    void voidPrescription_completedVisitBypassesToB2001() {
        // 126 轮 R-6：完成后（30）作废未收费处方是穿透 R15 的旁路——现在必须先被
        // requireVisitInProgress 拦截（B2001），而非走到 voidable 判定
        when(visitMapper.selectById(1L)).thenReturn(visit(1L, 30, 1L));
        when(prescriptionMapper.selectById(9L)).thenReturn(rx(9L, 10, 0, 1L)); // 未审核未收费
        BizException e = assertThrows(BizException.class,
                () -> svc().voidPrescription(9L, new PrescriptionVoidRequest()));
        assertEquals(ErrorCode.B2001, e.getErrorCode(), "126 轮 R-6：完成就诊拦截优先于可作废判定");
    }

    @Test
    void voidPrescription_pendingReviewSuccess() {
        when(visitMapper.selectById(1L)).thenReturn(visit(1L, 20, 1L));
        when(prescriptionMapper.selectById(9L)).thenReturn(rx(9L, 10, 0, 1L)); // 待审可作废
        // 条件更新成功（wrapper update 由 mapper.update(null, wrapper) 返回）
        when(prescriptionMapper.update(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> svc().voidPrescription(9L, new PrescriptionVoidRequest()));
        verify(prescriptionMapper).update(any(), any());
    }

    // ---------------- complete/saveRecord 断言（126 轮 R-2） ----------------

    @Test
    void complete_optimisticLockThrows() {
        CliVisit v = visit(1L, 20, 1L);
        v.setChiefComplaint("胸痛");
        v.setPresentIllness("现病史");
        when(visitMapper.selectById(1L)).thenReturn(v);
        when(visitMapper.selectById(1L)).thenReturn(v);
        when(diagnosisMapper.selectCount(any())).thenReturn(1L);
        when(visitMapper.updateById(any())).thenReturn(0); // 并发暂存抢先
        BizException e = assertThrows(BizException.class,
                () -> svc().complete(1L));
        assertEquals(ErrorCode.A0008, e.getErrorCode(), "126 轮 R-2：静默 0 行必须抛 A0008");
    }
}
