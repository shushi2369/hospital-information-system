package com.his.modules.pharmacy.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.clinic.app.ClinicAppService;
import com.his.modules.clinic.app.PharmacyRxDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.pharmacy.dto.ReturnRequest;
import com.his.modules.pharmacy.dto.ReviewRequest;
import com.his.modules.pharmacy.entity.InvInventoryBatch;
import com.his.modules.pharmacy.entity.PhrDispenseOrder;
import com.his.modules.pharmacy.entity.PhrReturnOrder;
import com.his.modules.pharmacy.mapper.InvInventoryBatchMapper;
import com.his.modules.pharmacy.mapper.InvStockMovementMapper;
import com.his.modules.pharmacy.mapper.PhrDispenseDetailMapper;
import com.his.modules.pharmacy.mapper.PhrDispenseOrderMapper;
import com.his.modules.pharmacy.mapper.PhrReturnDetailMapper;
import com.his.modules.pharmacy.mapper.PhrReturnOrderMapper;
import com.his.modules.pharmacy.mapper.PhrReviewRecordMapper;
import com.his.modules.system.app.SystemAppService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PharmacyService 单测：发药三重前置/FEFO 拆批/行锁复检 + 整方退药回补效期判定（113 轮 A-P2-1 固化）。 */
class PharmacyServiceTest extends UnitTestBase {

    @BeforeAll
    static void initMpLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PhrDispenseOrder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PhrReturnOrder.class);
    }

    private final ClinicAppService clinicAppService = mock(ClinicAppService.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final SystemAppService systemAppService = mock(SystemAppService.class);
    private final PhrReviewRecordMapper reviewRecordMapper = mock(PhrReviewRecordMapper.class);
    private final PhrDispenseOrderMapper dispenseOrderMapper = mock(PhrDispenseOrderMapper.class);
    private final PhrDispenseDetailMapper dispenseDetailMapper = mock(PhrDispenseDetailMapper.class);
    private final PhrReturnOrderMapper returnOrderMapper = mock(PhrReturnOrderMapper.class);
    private final PhrReturnDetailMapper returnDetailMapper = mock(PhrReturnDetailMapper.class);
    private final InvInventoryBatchMapper batchMapper = mock(InvInventoryBatchMapper.class);
    private final InvStockMovementMapper movementMapper = mock(InvStockMovementMapper.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private PharmacyService svc() {
        return new PharmacyService(clinicAppService, patientAppService, basedataAppService,
                systemAppService, reviewRecordMapper, dispenseOrderMapper, dispenseDetailMapper,
                returnOrderMapper, returnDetailMapper, batchMapper, movementMapper, idGenerator);
    }

    private PharmacyRxDTO rx(int status, Integer chargeStatus, String qty) {
        PharmacyRxDTO rx = new PharmacyRxDTO();
        rx.setId(60L);
        rx.setRxNo("RX001");
        rx.setPatientId(11L);
        rx.setStatus(status);
        rx.setChargeStatus(chargeStatus);
        PharmacyRxDTO.Item item = new PharmacyRxDTO.Item();
        item.setItemId(1L);
        item.setDrugId(20L);
        item.setDrugName("阿莫西林");
        item.setQuantity(new BigDecimal(qty));
        rx.setItems(new java.util.ArrayList<>(List.of(item)));
        return rx;
    }

    private InvInventoryBatch batch(long id, int status, String qty, LocalDate expiry) {
        InvInventoryBatch b = new InvInventoryBatch();
        b.setId(id);
        b.setDrugId(20L);
        b.setBatchNo("B" + id);
        b.setStatus(status);
        b.setQuantity(new BigDecimal(qty));
        b.setExpiryDate(expiry);
        return b;
    }

    private void stubPickable(InvInventoryBatch... batches) {
        when(batchMapper.selectPickable(20L)).thenReturn(List.of(batches));
        when(batchMapper.selectByIdForUpdate(anyLong()))
                .thenAnswer(inv -> {
                    long id = inv.getArgument(0);
                    return java.util.Arrays.stream(batches)
                            .filter(b -> b.getId() == id).findFirst().orElse(null);
                });
    }

    @Test
    void dispense_rxNotFound_throws() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void dispense_alreadyDispensed_throws() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(30, 1, "10"));
        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.B4003, e.getErrorCode());
    }

    @Test
    void dispense_notReviewed_throws() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(10, null, "10"));
        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.B4001, e.getErrorCode());
    }

    @Test
    void dispense_notCharged_throws() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 0, "10"));
        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.B4002, e.getErrorCode());
    }

    @Test
    void dispense_duplicateOrder_throws() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 1, "10"));
        when(dispenseOrderMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.B4003, e.getErrorCode());
    }

    @Test
    void dispense_nonPositiveQuantityItem_throws() {
        // 历史数据负数量明细：跳过扣减仍算发药成功是发药漏洞，必须整体拒绝
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 1, "-5"));
        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void dispense_happy_fefoSplitAcrossBatches() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 1, "12"));
        when(dispenseOrderMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("FY")).thenReturn("FY001");
        LocalDate future = LocalDate.now().plusDays(30);
        // FEFO：先效期批次先发——b1(早效期, 5) 耗尽置报废(4)，b2 补 7
        InvInventoryBatch b1 = batch(1, 1, "5", LocalDate.now().plusDays(10));
        InvInventoryBatch b2 = batch(2, 1, "10", future);
        stubPickable(b1, b2);

        String no = svc().dispense(60L);

        assertEquals("FY001", no);
        assertEquals(0, new BigDecimal("0").compareTo(b1.getQuantity()));
        assertEquals(4, b1.getStatus());
        assertEquals(0, new BigDecimal("3").compareTo(b2.getQuantity()));
        assertEquals(1, b2.getStatus());
        verify(movementMapper, times(2)).insert(any());
        verify(dispenseOrderMapper).insert(any(PhrDispenseOrder.class));
        verify(dispenseDetailMapper, times(2)).insert(any());
        verify(clinicAppService).markDispensed(60L);
    }

    @Test
    void dispense_lockedBatchInvalid_skipped() {
        // 行锁后复检：选批到锁行之间批次被停用 → 跳过该批继续 FEFO
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 1, "3"));
        when(dispenseOrderMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("FY")).thenReturn("FY002");
        InvInventoryBatch disabled = batch(1, 0, "99", LocalDate.now().plusDays(10));
        InvInventoryBatch good = batch(2, 1, "5", LocalDate.now().plusDays(10));
        stubPickable(disabled, good);

        svc().dispense(60L);

        assertEquals(0, new BigDecimal("2").compareTo(good.getQuantity()));
        verify(batchMapper, never()).updateById(eq(disabled));
    }

    @Test
    void dispense_insufficientStock_throws() {
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 1, "50"));
        when(dispenseOrderMapper.selectCount(any())).thenReturn(0L);
        stubPickable(batch(1, 1, "5", LocalDate.now().plusDays(10)));

        BizException e = assertThrows(BizException.class, () -> svc().dispense(60L));
        assertEquals(ErrorCode.B4004, e.getErrorCode());
        // 缺口时发药单不得落库、处方不得标记已发（扣减流水由 @Transactional 整体回滚，mock 层不模拟）
        verify(dispenseOrderMapper, never()).insert(any(PhrDispenseOrder.class));
        verify(clinicAppService, never()).markDispensed(any());
    }

    @Test
    void review_pass_writesAuditRecord() {
        ReviewRequest req = new ReviewRequest();
        req.setPass(true);
        req.setComment("OK");
        svc().review(60L, req);
        verify(clinicAppService).reviewPrescription(60L, true, 1L, "OK");
        // 留痕内容断言：pass→1（非 2）、处方号与意见落库（弱用例修复）
        ArgumentCaptor<com.his.modules.pharmacy.entity.PhrReviewRecord> captor =
                ArgumentCaptor.forClass(com.his.modules.pharmacy.entity.PhrReviewRecord.class);
        verify(reviewRecordMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getReviewAction());
        assertEquals(60L, captor.getValue().getPrescriptionId());
        assertEquals("OK", captor.getValue().getComment());
    }

    @Test
    void review_reject_writesAction2() {
        ReviewRequest req = new ReviewRequest();
        req.setPass(false);
        svc().review(60L, req);
        verify(clinicAppService).reviewPrescription(60L, false, 1L, null);
        ArgumentCaptor<com.his.modules.pharmacy.entity.PhrReviewRecord> captor =
                ArgumentCaptor.forClass(com.his.modules.pharmacy.entity.PhrReviewRecord.class);
        verify(reviewRecordMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getReviewAction());
    }

    @Test
    void dispense_expiryTodayBatch_skipped() {
        // 行锁复检边界：效期=今天视为过期（!isAfter(now)）必须跳过，只发后续效期批
        when(clinicAppService.getPrescriptionForDispense(60L)).thenReturn(rx(20, 1, "3"));
        when(dispenseOrderMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("FY")).thenReturn("FY003");
        InvInventoryBatch expiringToday = batch(1, 1, "99", LocalDate.now());
        InvInventoryBatch good = batch(2, 1, "5", LocalDate.now().plusDays(10));
        stubPickable(expiringToday, good);

        svc().dispense(60L);

        verify(batchMapper, never()).updateById(eq(expiringToday));
        assertEquals(0, new BigDecimal("2").compareTo(good.getQuantity()));
    }

    @Test
    void returnDrug_orderNotFound_throws() {
        when(dispenseOrderMapper.selectOne(any())).thenReturn(null);
        ReturnRequest req = new ReturnRequest();
        BizException e = assertThrows(BizException.class, () -> svc().returnDrug(req));
        assertEquals(ErrorCode.A0001, e.getErrorCode());
    }

    @Test
    void returnDrug_alreadyReturned_throws() {
        PhrDispenseOrder order = new PhrDispenseOrder();
        order.setId(80L);
        when(dispenseOrderMapper.selectOne(any())).thenReturn(order);
        when(returnOrderMapper.selectCount(any())).thenReturn(1L);
        ReturnRequest req = new ReturnRequest();
        BizException e = assertThrows(BizException.class, () -> svc().returnDrug(req));
        assertEquals(ErrorCode.B4006, e.getErrorCode());
    }

    @Test
    void returnDrug_happy_expiredBatchRestocksToStatus3() {
        // 113 轮 A-P2-1：发药后过期的批次回补为停用过期(3) 而非可用(1)
        PhrDispenseOrder order = new PhrDispenseOrder();
        order.setId(80L);
        order.setPrescriptionId(60L);
        order.setPatientId(11L);
        when(dispenseOrderMapper.selectOne(any())).thenReturn(order);
        when(returnOrderMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("TY")).thenReturn("TY001");
        InvInventoryBatch expired = batch(1, 4, "0", LocalDate.now().minusDays(2));
        when(batchMapper.selectByIdForUpdate(1L)).thenReturn(expired);
        when(dispenseDetailMapper.selectList(any())).thenReturn(List.of(detail(1L, 20L, "3")));

        String no = svc().returnDrug(new ReturnRequest());

        assertEquals("TY001", no);
        assertEquals(0, new BigDecimal("3").compareTo(expired.getQuantity()));
        assertEquals(3, expired.getStatus());
        verify(returnOrderMapper).insert(any(PhrReturnOrder.class));
        verify(returnDetailMapper).insert(any());
        verify(movementMapper).insert(any());
        verify(clinicAppService).markPrescriptionReturned(60L);
    }

    @Test
    void returnDrug_happy_validBatchRestocksToStatus1() {
        PhrDispenseOrder order = new PhrDispenseOrder();
        order.setId(80L);
        order.setPrescriptionId(60L);
        order.setPatientId(11L);
        when(dispenseOrderMapper.selectOne(any())).thenReturn(order);
        when(returnOrderMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.next("TY")).thenReturn("TY002");
        InvInventoryBatch depleted = batch(1, 4, "0", LocalDate.now().plusDays(30));
        when(batchMapper.selectByIdForUpdate(1L)).thenReturn(depleted);
        when(dispenseDetailMapper.selectList(any())).thenReturn(List.of(detail(1L, 20L, "6")));

        svc().returnDrug(new ReturnRequest());

        assertEquals(0, new BigDecimal("6").compareTo(depleted.getQuantity()));
        assertEquals(1, depleted.getStatus());
        verify(clinicAppService).markPrescriptionReturned(60L);
    }

    private com.his.modules.pharmacy.entity.PhrDispenseDetail detail(
            long batchId, long drugId, String qty) {
        com.his.modules.pharmacy.entity.PhrDispenseDetail d =
                new com.his.modules.pharmacy.entity.PhrDispenseDetail();
        d.setBatchId(batchId);
        d.setDrugId(drugId);
        d.setQuantity(new BigDecimal(qty));
        return d;
    }
}
