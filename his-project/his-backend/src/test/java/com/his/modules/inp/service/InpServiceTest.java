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
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.inp.mapper.InpTransferMapper;
import com.his.modules.inp.mapper.InpWardMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.patient.app.PatientDTO;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** InpService 入院登记单测：患者行锁顺序（三十一轮修复）+ 病区/床位校验 + 占床失败。 */
class InpServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        // 纯 Mockito 环境无 MP 容器：LambdaUpdateWrapper.set(实体::getter) 需要实体 lambda cache
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        for (Class<?> entity : new Class<?>[]{com.his.modules.inp.entity.InpAdmission.class}) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    private final InpAdmissionMapper admissionMapper = mock(InpAdmissionMapper.class);
    private final InpBedMapper bedMapper = mock(InpBedMapper.class);
    private final InpWardMapper wardMapper = mock(InpWardMapper.class);
    private final InpTransferMapper transferMapper = mock(InpTransferMapper.class);
    private final InpDepositMapper depositMapper = mock(InpDepositMapper.class);
    private final BilChargeBillMapper billMapper = mock(BilChargeBillMapper.class);
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
            admissionMapper, bedMapper, wardMapper, transferMapper, depositMapper, billMapper,
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
    void discharge_zeroUnpaidFeesAutoSettles() {
        // 当日入出且无费用：出院直接闭环 30，无需结算单（三十九轮 UI 走查实锤的卡死场景）
        InpAdmission admission = new InpAdmission();
        admission.setId(8641L);
        admission.setStatus(10);
        admission.setBedId(300L);
        admission.setDepositTotal(new java.math.BigDecimal("500"));
        when(inpAppService.requireInHospital(8641L)).thenReturn(admission);
        when(dischargeHooks.iterator()).thenReturn(java.util.Collections.<com.his.modules.inp.spi.DischargeCheckHook>emptyList().iterator());
        when(bedMapper.releaseBed(300L, 8641L)).thenReturn(1);
        when(dailyFeeMapper.selectCount(any())).thenReturn(0L);
        when(admissionMapper.update(any(), any())).thenReturn(1);

        com.his.modules.inp.dto.DischargeRequest req = new com.his.modules.inp.dto.DischargeRequest();
        req.setDischargeWay(1);
        req.setDischargeDiagnosis("治愈出院");
        service.discharge(8641L, req);

        // 窄列更新：状态落在 wrapper 参数里，不再回写内存实体
        assertEquals(30, capturedDischargeStatus());
        verify(bedMapper).releaseBed(300L, 8641L);
    }

    @Test
    void discharge_withUnpaidFeesGoesTo20() {
        InpAdmission admission = new InpAdmission();
        admission.setId(8642L);
        admission.setStatus(10);
        admission.setBedId(301L);
        when(inpAppService.requireInHospital(8642L)).thenReturn(admission);
        when(dischargeHooks.iterator()).thenReturn(java.util.Collections.<com.his.modules.inp.spi.DischargeCheckHook>emptyList().iterator());
        when(bedMapper.releaseBed(301L, 8642L)).thenReturn(1);
        when(dailyFeeMapper.selectCount(any())).thenReturn(2L); // 有未结费用 → 待结算
        when(admissionMapper.update(any(), any())).thenReturn(1);

        com.his.modules.inp.dto.DischargeRequest req = new com.his.modules.inp.dto.DischargeRequest();
        req.setDischargeWay(1);
        req.setDischargeDiagnosis("未愈转院");
        service.discharge(8642L, req);

        assertEquals(20, capturedDischargeStatus());
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

    /** 捕获 discharge 窄列更新 wrapper 中的 status 参数值 */
    @SuppressWarnings("unchecked")
    private Integer capturedDischargeStatus() {
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<InpAdmission>> captor =
                org.mockito.ArgumentCaptor.forClass(
                        (Class<com.baomidou.mybatisplus.core.conditions.Wrapper<InpAdmission>>) (Class<?>) com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        org.mockito.Mockito.verify(admissionMapper, org.mockito.Mockito.atLeastOnce())
                .update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InpAdmission> w =
                (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InpAdmission>) captor.getValue();
        return (Integer) w.getParamNameValuePairs().values().stream()
                .filter(v -> v instanceof Integer && ((Integer) v) == 30 || v instanceof Integer && ((Integer) v) == 20)
                .findFirst().orElseThrow();
    }
}
