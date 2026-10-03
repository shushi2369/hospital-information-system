package com.his.modules.billing.service;

import com.his.UnitTestBase;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.billing.dto.ChargeRequest;
import com.his.modules.billing.dto.RefundRequest;
import com.his.modules.billing.dto.SettlementRequest;
import com.his.modules.billing.entity.BilChargeBill;
import com.his.modules.billing.entity.BilChargeDetail;
import com.his.modules.billing.entity.BilRefundBill;
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.billing.mapper.BilChargeDetailMapper;
import com.his.modules.billing.mapper.BilDailySettlementMapper;
import com.his.modules.billing.mapper.BilPaymentRecordMapper;
import com.his.modules.billing.mapper.BilRefundBillMapper;
import com.his.modules.billing.mapper.BilRefundDetailMapper;
import com.his.modules.clinic.app.BillingVisitDTO;
import com.his.modules.clinic.app.ClinicAppService;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.inp.entity.InpAdmission;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.plt.service.PltService;
import com.his.modules.registration.app.RegistrationAppService;
import com.his.modules.ris.service.RisService;
import com.his.modules.system.app.SystemAppService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** BillingService 核心逻辑单测：收费幂等 + 退费逐行/整方/已发药门禁 + 日结与出院结算状态机。 */
class BillingServiceTest extends UnitTestBase {

    private final BilChargeBillMapper billMapper = mock(BilChargeBillMapper.class);
    private final BilChargeDetailMapper chargeDetailMapper = mock(BilChargeDetailMapper.class);
    private final BilPaymentRecordMapper paymentRecordMapper = mock(BilPaymentRecordMapper.class);
    private final BilRefundBillMapper refundBillMapper = mock(BilRefundBillMapper.class);
    private final BilRefundDetailMapper refundDetailMapper = mock(BilRefundDetailMapper.class);
    private final BilDailySettlementMapper settlementMapper = mock(BilDailySettlementMapper.class);
    private final ClinicAppService clinicAppService = mock(ClinicAppService.class);
    private final RegistrationAppService registrationAppService = mock(RegistrationAppService.class);
    private final PatientAppService patientAppService = mock(PatientAppService.class);
    private final SystemAppService systemAppService = mock(SystemAppService.class);
    private final PltService pltService = mock(PltService.class);
    private final RisService risAppService = mock(RisService.class);
    private final BasedataAppService basedataAppService = mock(BasedataAppService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final InpAppService inpAppService = mock(InpAppService.class);

    private final BillingService service = new BillingService(
            billMapper, chargeDetailMapper, paymentRecordMapper, refundBillMapper,
            refundDetailMapper, settlementMapper, clinicAppService, registrationAppService,
            patientAppService, systemAppService, pltService, risAppService,
            basedataAppService, idGenerator, inpAppService);

    private BillingVisitDTO visit() {
        BillingVisitDTO v = new BillingVisitDTO();
        v.setId(9L);
        v.setPatientId(3L);
        v.setVisitStatus(10);
        return v;
    }

    private BilChargeBill bill() {
        BilChargeBill b = new BilChargeBill();
        b.setId(1L);
        b.setVisitId(9L);
        b.setPatientId(3L);
        b.setCashierId(1L);
        b.setTotalAmount(new BigDecimal("20.00"));
        b.setPayableAmount(new BigDecimal("20.00"));
        b.setRefundAmount(BigDecimal.ZERO);
        b.setStatus(10);
        return b;
    }

    private BilChargeDetail detail(Long id, int feeType, BigDecimal qty, String price) {
        BilChargeDetail d = new BilChargeDetail();
        d.setId(id);
        d.setBillId(1L);
        d.setFeeType(feeType);
        d.setSourceType(feeType == 7 ? 2 : 5);
        d.setSourceDetailId(id + 100);
        d.setItemName("项目" + id);
        d.setQuantity(qty);
        d.setUnitPrice(new BigDecimal(price));
        d.setRefundStatus(0);
        d.setStatus(1);
        return d;
    }

    private RefundRequest refundReq(RefundRequest.Line... lines) {
        RefundRequest r = new RefundRequest();
        r.setBillId(1L);
        r.setReason("单测退费");
        r.setDetails(List.of(lines));
        return r;
    }

    private RefundRequest.Line line(Long detailId, String qty) {
        RefundRequest.Line l = new RefundRequest.Line();
        l.setChargeDetailId(detailId);
        l.setRefundQuantity(new BigDecimal(qty));
        return l;
    }

    private void stubRefundCommon(BilChargeBill bill) {
        when(billMapper.selectByIdForUpdate(1L)).thenReturn(bill);
        when(clinicAppService.getVisitForBilling(9L)).thenReturn(visit());
        when(clinicAppService.prescriptionStatusByItemIds(anyList())).thenReturn(Map.of());
        when(clinicAppService.prescriptionIdByItemIds(anyList())).thenReturn(Map.of());
    }

    @Test
    void charge_duplicateVisitRejected() {
        when(clinicAppService.getVisitForBilling(9L)).thenReturn(visit());
        when(billMapper.selectCount(any())).thenReturn(1L);
        ChargeRequest req = new ChargeRequest();
        req.setVisitId(9L);
        req.setPayMethod(1);

        BizException e = assertThrows(BizException.class, () -> service.charge(req));
        assertEquals(ErrorCode.B3001, e.getErrorCode());
        verify(chargeDetailMapper, never()).insert(any(BilChargeDetail.class));
        verify(paymentRecordMapper, never()).insert(any());
    }

    @Test
    void refund_overAvailableQtyRejected() {
        stubRefundCommon(bill());
        when(chargeDetailMapper.selectList(any())).thenReturn(List.of(detail(11L, 5, new BigDecimal("2"), "10.00")));

        BizException e = assertThrows(BizException.class,
                () -> service.refund(refundReq(line(11L, "3"))));
        assertTrue(e.getMessage().contains("超过可退数量"));
        verify(refundBillMapper, never()).insert(any(BilRefundBill.class));
    }

    @Test
    void refund_dispensedRxMustReturnDrugFirst() {
        stubRefundCommon(bill());
        BilChargeDetail d = detail(11L, 7, new BigDecimal("2"), "10.00"); // sourceDetailId 111
        when(chargeDetailMapper.selectList(any())).thenReturn(List.of(d));
        when(clinicAppService.prescriptionStatusByItemIds(anyList())).thenReturn(Map.of(111L, 30)); // 已发药
        when(clinicAppService.prescriptionIdByItemIds(anyList())).thenReturn(Map.of(111L, 9L));

        BizException e = assertThrows(BizException.class,
                () -> service.refund(refundReq(line(11L, "2"))));
        assertTrue(e.getMessage().contains("已发药"));
        verify(refundBillMapper, never()).insert(any(BilRefundBill.class));
    }

    @Test
    void refund_partialRxRejectedByWholeRule() {
        stubRefundCommon(bill());
        BilChargeDetail d1 = detail(11L, 7, new BigDecimal("10"), "1.00"); // 同处方 rx9 的两行
        BilChargeDetail d2 = detail(12L, 7, new BigDecimal("5"), "1.00");
        when(chargeDetailMapper.selectList(any())).thenReturn(List.of(d1, d2));
        when(clinicAppService.prescriptionStatusByItemIds(anyList())).thenReturn(Map.of(111L, 10, 112L, 10));
        when(clinicAppService.prescriptionIdByItemIds(anyList())).thenReturn(Map.of(111L, 9L, 112L, 9L));

        BizException e = assertThrows(BizException.class,
                () -> service.refund(refundReq(line(11L, "10")))); // 只退第一行
        assertTrue(e.getMessage().contains("整方"));
        verify(refundBillMapper, never()).insert(any(BilRefundBill.class));
    }

    @Test
    void settle_duplicateSameDayRejected() {
        when(settlementMapper.selectCount(any())).thenReturn(1L);
        SettlementRequest req = new SettlementRequest();
        req.setSettleDate(LocalDate.now());

        BizException e = assertThrows(BizException.class, () -> service.settle(req));
        assertEquals(ErrorCode.B3006, e.getErrorCode());
        verify(settlementMapper, never()).insert(any());
    }

    @Test
    void settleAdmission_statusGatesRejected() {
        InpAppService.AdmissionView settled = new InpAppService.AdmissionView();
        InpAdmission done = new InpAdmission();
        done.setStatus(30);
        settled.setAdmission(done);
        when(inpAppService.getAdmissionView(7L)).thenReturn(settled);
        assertEquals(ErrorCode.B3001, assertThrows(BizException.class,
                () -> service.settleAdmission(7L, 1)).getErrorCode());

        InpAppService.AdmissionView inHouse = new InpAppService.AdmissionView();
        InpAdmission active = new InpAdmission();
        active.setStatus(10);
        inHouse.setAdmission(active);
        when(inpAppService.getAdmissionView(7L)).thenReturn(inHouse);
        assertEquals(ErrorCode.B6003, assertThrows(BizException.class,
                () -> service.settleAdmission(7L, 1)).getErrorCode());
        verify(chargeDetailMapper, never()).insert(any(BilChargeDetail.class));
    }

    /** 四十九轮：标记收窄 + 残留复查——结算窗口期新增费用必须拒绝，不允许静默遗留 */
    @Test
    void settleAdmission_newFeeDuringSettleAborts() {
        InpAppService.AdmissionView view = dischargedView();
        when(inpAppService.getAdmissionView(7L)).thenReturn(view);
        when(billMapper.selectCount(any())).thenReturn(0L);
        when(inpAppService.listUnpaidDailyFees(7L)).thenReturn(List.of(fee(501L)));
        when(inpAppService.countUnpaidDailyFees(7L)).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.settleAdmission(7L, 1));
        assertEquals(ErrorCode.B3002, e.getErrorCode());
        assertTrue(e.getMessage().contains("新增费用"));
        verify(inpAppService, never()).markSettled(any());
    }

    /** 对照：无窗口期新增 → 仅标记账单包含的费用并完成结算 */
    @Test
    void settleAdmission_marksOnlyBillFeesAndSettles() {
        InpAppService.AdmissionView view = dischargedView();
        when(inpAppService.getAdmissionView(7L)).thenReturn(view);
        when(billMapper.selectCount(any())).thenReturn(0L);
        when(inpAppService.listUnpaidDailyFees(7L)).thenReturn(List.of(fee(501L)));
        when(inpAppService.countUnpaidDailyFees(7L)).thenReturn(0L);

        var resp = service.settleAdmission(7L, 1);
        assertEquals(new BigDecimal("50.00"), resp.getTotalAmount());
        verify(inpAppService).markDailyFeesSettled(
                argThat((java.util.Collection<Long> ids) -> ids != null && ids.contains(501L)));
        verify(inpAppService).markSettled(7L);
    }

    /** 五十轮：日结锁定——本人当日已日结后，收费/退费/住院结算一律拒绝（B3006），否则交易落在任何日结之外 */
    @Test
    void charge_blockedAfterDailySettlement() {
        when(settlementMapper.selectCount(any())).thenReturn(1L);
        ChargeRequest req = new ChargeRequest();
        req.setVisitId(9L);
        req.setPayMethod(1);

        BizException e = assertThrows(BizException.class, () -> service.charge(req));
        assertEquals(ErrorCode.B3006, e.getErrorCode());
        verify(billMapper, never()).insert(any(BilChargeBill.class));
    }

    @Test
    void refund_blockedAfterDailySettlement() {
        when(settlementMapper.selectCount(any())).thenReturn(1L);
        RefundRequest req = new RefundRequest();
        req.setBillId(1L);

        BizException e = assertThrows(BizException.class, () -> service.refund(req));
        assertEquals(ErrorCode.B3006, e.getErrorCode());
        verify(refundBillMapper, never()).insert(any(BilRefundBill.class));
    }

    @Test
    void settleAdmission_blockedAfterDailySettlement() {
        when(settlementMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.settleAdmission(7L, 1));
        assertEquals(ErrorCode.B3006, e.getErrorCode());
        verify(chargeDetailMapper, never()).insert(any(BilChargeDetail.class));
    }

    /** 五十二轮：IDOR——账单详情按 id 直查也要过数据范围（列表过滤≠详情过滤） */
    @Test
    void billDetail_foreignBillRejectedForCashier() {
        setupAs(9L, "CASHIER");
        when(billMapper.selectById(1L)).thenReturn(bill()); // bill() cashierId=1 ≠ 当前用户 9

        assertEquals(ErrorCode.A0003, assertThrows(BizException.class,
                () -> service.billDetail(1L)).getErrorCode());
    }

    @Test
    void billDetail_adminCanReadForeignBill() {
        when(billMapper.selectById(1L)).thenReturn(bill()); // 当前用户 ADMIN(1)，账单 cashier=1
        when(patientAppService.getById(3L)).thenReturn(null);
        when(systemAppService.getUsernameMap(anyList())).thenReturn(Map.of());

        assertEquals(1L, service.billDetail(1L).getId());
    }

    private InpAppService.AdmissionView dischargedView() {
        InpAppService.AdmissionView view = new InpAppService.AdmissionView();
        InpAdmission discharged = new InpAdmission();
        discharged.setId(7L);
        discharged.setStatus(20);
        discharged.setPatientId(3L);
        view.setAdmission(discharged);
        return view;
    }

    private com.his.modules.inp.app.DailyFeeDTO fee(Long id) {
        com.his.modules.inp.app.DailyFeeDTO f = new com.his.modules.inp.app.DailyFeeDTO();
        f.setId(id);
        f.setFeeType(1);
        f.setItemName("护理费");
        f.setQuantity(BigDecimal.ONE);
        f.setUnitPrice(new BigDecimal("50.00"));
        f.setAmount(new BigDecimal("50.00"));
        return f;
    }
}
