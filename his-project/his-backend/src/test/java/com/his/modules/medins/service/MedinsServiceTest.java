package com.his.modules.medins.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.billing.entity.BilChargeBill;
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.medins.entity.MedinsSettle;
import com.his.modules.medins.gateway.MedInsuranceGateway;
import com.his.modules.medins.mapper.MedinsSettleMapper;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.plt.service.PltService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MedinsService 单测：医保申报净额门禁/重复申报/差异重报 + 对账状态机（113 轮 P0 修复固化）。 */
class MedinsServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MedinsSettle.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BilChargeBill.class);
    }

    private final MedinsSettleMapper settleMapper = mock(MedinsSettleMapper.class);
    private final BilChargeBillMapper billMapper = mock(BilChargeBillMapper.class);
    private final InpAppService inpAppService = mock(InpAppService.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final MedInsuranceGateway gateway = mock(MedInsuranceGateway.class);
    private final PltService pltService = mock(PltService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private MedinsService svc() {
        return new MedinsService(settleMapper, billMapper, inpAppService, patientAppService,
                gateway, pltService, idGenerator);
    }

    private BilChargeBill bill(int status, String payable, String refund) {
        BilChargeBill b = new BilChargeBill();
        b.setId(500L);
        b.setAdmissionId(900L);
        b.setStatus(status);
        b.setPayableAmount(new BigDecimal(payable));
        b.setRefundAmount(new BigDecimal(refund));
        return b;
    }

    private void stubAdmission() {
        InpAdmission admission = new InpAdmission();
        admission.setId(900L);
        when(inpAppService.requireAdmission(900L)).thenReturn(admission);
    }

    private MedinsSettle existing(long id, int status) {
        MedinsSettle s = new MedinsSettle();
        s.setId(id);
        s.setBillId(500L);
        s.setStatus(status);
        s.setReconcileTime(java.time.LocalDateTime.now());
        s.setDiffReason("对账差异");
        return s;
    }

    @Test
    void apply_billNotFound_throws() {
        when(billMapper.selectById(500L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().apply(500L, 1));
        assertEquals(ErrorCode.B3007, e.getErrorCode());
    }

    @Test
    void apply_fullyRefundedBill_throws() {
        when(billMapper.selectById(500L)).thenReturn(bill(30, "1000", "200"));
        BizException e = assertThrows(BizException.class, () -> svc().apply(500L, 1));
        assertEquals(ErrorCode.B6402, e.getErrorCode());
    }

    @Test
    void apply_duplicateNonDiff_throws() {
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(settleMapper.selectOne(any())).thenReturn(existing(7L, 20));
        BizException e = assertThrows(BizException.class, () -> svc().apply(500L, 1));
        assertEquals(ErrorCode.B6401, e.getErrorCode());
    }

    @Test
    void apply_refundCoversAll_netZero_throws() {
        // 部分退费(20)允许申报，但退满后净额为 0 仍不可申报（防医保多付）
        when(billMapper.selectById(500L)).thenReturn(bill(20, "1000", "1000"));
        BizException e = assertThrows(BizException.class, () -> svc().apply(500L, 1));
        assertEquals(ErrorCode.B6402, e.getErrorCode());
    }

    @Test
    void apply_happyPath_newSettle_appliesNetAmount() {
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "200"));
        when(settleMapper.selectOne(any())).thenReturn(null);
        stubAdmission();
        when(idGenerator.next("YB")).thenReturn("YB20261024001");
        when(gateway.apply(new BigDecimal("800"), 1))
                .thenReturn(new MedInsuranceGateway.ApplyResult(
                        new BigDecimal("600"), new BigDecimal("150"), new BigDecimal("50")));

        String settleNo = svc().apply(500L, 1);

        assertEquals("YB20261024001", settleNo);
        ArgumentCaptor<MedinsSettle> captor = ArgumentCaptor.forClass(MedinsSettle.class);
        verify(settleMapper).insert(captor.capture());
        MedinsSettle inserted = captor.getValue();
        assertEquals(0, new BigDecimal("800").compareTo(inserted.getTotalAmount()));
        assertEquals(10, inserted.getStatus());
        assertEquals(1, inserted.getInsuranceType());
        assertEquals(900L, inserted.getAdmissionId());
        verify(pltService).recordEvent(eq("medins.settle.applied"), eq("YB20261024001"), anyString());
    }

    @Test
    void apply_diffRereport_reusesExistingAndResetsDiff() {
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(settleMapper.selectOne(any())).thenReturn(existing(7L, 30));
        stubAdmission();
        when(settleMapper.updateById(any(MedinsSettle.class))).thenReturn(1);
        when(gateway.apply(any(), anyInt()))
                .thenReturn(new MedInsuranceGateway.ApplyResult(
                        new BigDecimal("800"), BigDecimal.ZERO, new BigDecimal("200")));

        svc().apply(500L, 1);

        ArgumentCaptor<MedinsSettle> captor = ArgumentCaptor.forClass(MedinsSettle.class);
        verify(settleMapper).updateById(captor.capture());
        MedinsSettle updated = captor.getValue();
        assertEquals(7L, updated.getId());
        assertEquals(10, updated.getStatus());
        assertNull(updated.getReconcileTime());
        assertNull(updated.getDiffReason());
        verify(settleMapper, never()).insert(any());
    }

    @Test
    void apply_rereport_concurrentZeroRow_throws() {
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(settleMapper.selectOne(any())).thenReturn(existing(7L, 30));
        stubAdmission();
        when(settleMapper.updateById(any(MedinsSettle.class))).thenReturn(0);
        when(gateway.apply(any(), anyInt()))
                .thenReturn(new MedInsuranceGateway.ApplyResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        BizException e = assertThrows(BizException.class, () -> svc().apply(500L, 1));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void apply_raceDuplicateKey_throws() {
        // 防并发双申报的唯一兜底：uk 撞击 → B6401
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(settleMapper.selectOne(any())).thenReturn(null);
        stubAdmission();
        when(idGenerator.next("YB")).thenReturn("YB001");
        when(gateway.apply(any(), anyInt()))
                .thenReturn(new MedInsuranceGateway.ApplyResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(settleMapper.insert(any(MedinsSettle.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        BizException e = assertThrows(BizException.class, () -> svc().apply(500L, 1));
        assertEquals(ErrorCode.B6401, e.getErrorCode());
    }

    @Test
    void reconcile_settleNotFound_throws() {
        when(settleMapper.selectById(7L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().reconcile(7L, null));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void reconcile_notPending_throws() {
        when(settleMapper.selectById(7L)).thenReturn(existing(7L, 20));
        BizException e = assertThrows(BizException.class, () -> svc().reconcile(7L, null));
        assertEquals(ErrorCode.B6403, e.getErrorCode());
    }

    @Test
    void reconcile_pass_savesRemark_setsPassed() {
        MedinsSettle settle = existing(7L, 10);
        settle.setSettleNo("YB001");
        when(settleMapper.selectById(7L)).thenReturn(settle);
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(gateway.reconcile(eq("YB001"), any(), any())).thenReturn(true);
        when(settleMapper.updateById(any(MedinsSettle.class))).thenReturn(1);

        com.his.modules.medins.dto.ReconcileRequest req = new com.his.modules.medins.dto.ReconcileRequest();
        req.setRemark("  网关比对一致  ");
        svc().reconcile(7L, req);

        ArgumentCaptor<MedinsSettle> captor = ArgumentCaptor.forClass(MedinsSettle.class);
        verify(settleMapper).updateById(captor.capture());
        assertEquals(20, captor.getValue().getStatus());
        assertEquals("网关比对一致", captor.getValue().getDiffReason());
        assertNotNull(captor.getValue().getReconcileTime());
    }

    @Test
    void reconcile_fail_wrapsRemarkIntoDiffReason() {
        MedinsSettle settle = existing(7L, 10);
        settle.setSettleNo("YB001");
        when(settleMapper.selectById(7L)).thenReturn(settle);
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(gateway.reconcile(eq("YB001"), any(), any())).thenReturn(false);
        when(settleMapper.updateById(any(MedinsSettle.class))).thenReturn(1);

        com.his.modules.medins.dto.ReconcileRequest req = new com.his.modules.medins.dto.ReconcileRequest();
        req.setRemark("金额不符");
        svc().reconcile(7L, req);

        ArgumentCaptor<MedinsSettle> captor = ArgumentCaptor.forClass(MedinsSettle.class);
        verify(settleMapper).updateById(captor.capture());
        assertEquals(30, captor.getValue().getStatus());
        assertTrue(captor.getValue().getDiffReason().contains("金额不符"));
    }

    @Test
    void reconcile_fail_noRemark_defaultReason() {
        MedinsSettle settle = existing(7L, 10);
        settle.setSettleNo("YB001");
        when(settleMapper.selectById(7L)).thenReturn(settle);
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(gateway.reconcile(eq("YB001"), any(), any())).thenReturn(false);
        when(settleMapper.updateById(any(MedinsSettle.class))).thenReturn(1);

        svc().reconcile(7L, null);

        ArgumentCaptor<MedinsSettle> captor = ArgumentCaptor.forClass(MedinsSettle.class);
        verify(settleMapper).updateById(captor.capture());
        assertEquals(30, captor.getValue().getStatus());
        assertTrue(captor.getValue().getDiffReason().contains("申报金额与账单不一致"));
    }

    @Test
    void reconcile_concurrentZeroRow_throws() {
        MedinsSettle settle = existing(7L, 10);
        settle.setSettleNo("YB001");
        when(settleMapper.selectById(7L)).thenReturn(settle);
        when(billMapper.selectById(500L)).thenReturn(bill(10, "1000", "0"));
        when(gateway.reconcile(eq("YB001"), any(), any())).thenReturn(true);
        when(settleMapper.updateById(any(MedinsSettle.class))).thenReturn(0);

        BizException e = assertThrows(BizException.class, () -> svc().reconcile(7L, null));
        assertEquals(ErrorCode.A0008, e.getErrorCode());
    }

    @Test
    void apply_partialRefund_appliesNetNotGross() {
        when(billMapper.selectById(500L)).thenReturn(bill(20, "1000", "300"));
        when(settleMapper.selectOne(any())).thenReturn(null);
        stubAdmission();
        when(idGenerator.next("YB")).thenReturn("YB002");
        when(gateway.apply(new BigDecimal("700"), 2))
                .thenReturn(new MedInsuranceGateway.ApplyResult(
                        new BigDecimal("500"), new BigDecimal("100"), new BigDecimal("100")));

        svc().apply(500L, 2);

        // 申报按净额（应收-已退）而非原始应收
        verify(gateway).apply(new BigDecimal("700"), 2);
        ArgumentCaptor<MedinsSettle> captor = ArgumentCaptor.forClass(MedinsSettle.class);
        verify(settleMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("700").compareTo(captor.getValue().getTotalAmount()));
    }
}
