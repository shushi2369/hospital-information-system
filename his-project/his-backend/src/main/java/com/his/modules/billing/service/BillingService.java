package com.his.modules.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.billing.dto.BillQuery;
import com.his.modules.billing.dto.BillResponse;
import com.his.modules.billing.dto.ChargeRequest;
import com.his.modules.billing.dto.PayableResultDTO;
import com.his.modules.billing.dto.RefundQuery;
import com.his.modules.billing.dto.RefundRequest;
import com.his.modules.billing.dto.SettlementQuery;
import com.his.modules.billing.dto.SettlementRequest;
import com.his.modules.billing.entity.BilChargeBill;
import com.his.modules.billing.entity.BilChargeDetail;
import com.his.modules.billing.entity.BilDailySettlement;
import com.his.modules.billing.entity.BilPaymentRecord;
import com.his.modules.billing.entity.BilRefundBill;
import com.his.modules.billing.entity.BilRefundDetail;
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.billing.mapper.BilChargeDetailMapper;
import com.his.modules.billing.mapper.BilDailySettlementMapper;
import com.his.modules.billing.mapper.BilPaymentRecordMapper;
import com.his.modules.billing.mapper.BilRefundBillMapper;
import com.his.modules.billing.mapper.BilRefundDetailMapper;
import com.his.modules.clinic.app.BillingRxItemDTO;
import com.his.modules.clinic.app.BillingVisitDTO;
import com.his.modules.clinic.app.ClinicAppService;
import com.his.modules.clinic.app.UnpaidVisitDTO;
import com.his.modules.patient.app.PatientAppService;
import com.his.modules.registration.app.RegistrationAppService;
import com.his.modules.registration.app.RegistrationDTO;
import com.his.modules.system.app.SystemAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收费结算服务（B-01~B-08，规则《05》R8~R12）。
 * 事务边界：收费/退费均为原子事务，失败整体回滚（《01》§6.5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {
    private final BilChargeBillMapper billMapper;
    private final BilChargeDetailMapper chargeDetailMapper;
    private final BilPaymentRecordMapper paymentRecordMapper;
    private final BilRefundBillMapper refundBillMapper;
    private final BilRefundDetailMapper refundDetailMapper;
    private final BilDailySettlementMapper settlementMapper;
    private final ClinicAppService clinicAppService;
    private final RegistrationAppService registrationAppService;
    private final PatientAppService patientAppService;
    private final SystemAppService systemAppService;
    private final com.his.modules.plt.service.PltService pltService;
    private final com.his.modules.ris.service.RisService risAppService;
    private final BasedataAppService basedataAppService;
    private final IdGenerator idGenerator;
    private final com.his.modules.inp.app.InpAppService inpAppService;

    /** 未收费就诊列表（B-02 收费窗口工作队列） */
    public List<Map<String, Object>> unpaidVisits() {
        // 量级边界：payable() 为金额关键单就诊计算（与 charge 共用 buildPayableItems），此处逐单调用
        // 是刻意保留——不做跨就诊批量化以免复制资金汇总逻辑；列表上限 100，实测 3 条 ≈100ms 线性
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (UnpaidVisitDTO v : clinicAppService.listVisitsWithUnpaidItems()) {
            Map<String, Object> row = new HashMap<>();
            row.put("visitId", v.getVisitId());
            row.put("visitNo", v.getVisitNo());
            row.put("patientName", v.getPatientName());
            row.put("visitDate", v.getVisitDate());
            row.put("doctorName", v.getDoctorName());
            row.put("unpaidAmount", payable(v.getVisitId()).getTotalAmount());
            result.put(v.getVisitId(), row);
        }
        for (RegistrationDTO reg : registrationAppService.listVisitedUnpaid()) {
            BillingVisitDTO visit = clinicAppService.getVisitByRegistrationId(reg.getId());
            if (visit == null || result.containsKey(visit.getId())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("visitId", visit.getId());
            row.put("visitNo", visit.getVisitNo());
            row.put("patientName", visit.getPatientName());
            row.put("visitDate", visit.getVisitDate());
            row.put("doctorName", visit.getDoctorName());
            row.put("unpaidAmount", payable(visit.getId()).getTotalAmount());
            result.put(visit.getId(), row);
        }
        return new ArrayList<>(result.values());
    }

    /** 待缴费清单（B-01）：实时汇总未收费的挂号费/诊查费/处方明细/检查申请 */
    public PayableResultDTO payable(Long visitId) {
        BillingVisitDTO visit = requireVisit(visitId);
        PayableResultDTO result = new PayableResultDTO();
        result.setVisitId(visitId);
        result.setVisitNo(visit.getVisitNo());
        result.setPatientName(visit.getPatientName());
        List<PayableResultDTO.PayableItem> items = buildPayableItems(visit);
        BigDecimal total = items.stream().map(PayableResultDTO.PayableItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.setItems(items);
        result.setTotalAmount(total);
        return result;
    }

    /** 收费（B-02）：单据+明细+支付记录同事务生成，并联动标记来源已收费 */
    @Transactional
    public BillResponse charge(ChargeRequest req) {
        BillingVisitDTO visit = requireVisit(req.getVisitId());
        if (billMapper.selectCount(new LambdaQueryWrapper<BilChargeBill>()
                .eq(BilChargeBill::getVisitId, req.getVisitId())) > 0) {
            throw new BizException(ErrorCode.B3001);
        }
        List<PayableResultDTO.PayableItem> items = buildPayableItems(visit);
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.B3002);
        }
        Long cashierId = CurrentUser.id();
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        BilChargeBill bill = new BilChargeBill();
        bill.setBillNo(idGenerator.next("SF"));
        bill.setVisitId(visit.getId());
        bill.setPatientId(visit.getPatientId());
        bill.setTotalAmount(items.get(0) == null ? BigDecimal.ZERO
                : items.stream().map(PayableResultDTO.PayableItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        bill.setDiscountAmount(BigDecimal.ZERO);
        bill.setPayableAmount(bill.getTotalAmount());
        bill.setPaidAmount(bill.getTotalAmount());
        bill.setRefundAmount(BigDecimal.ZERO);
        bill.setPayMethod(req.getPayMethod());
        bill.setPayTime(now);
        bill.setCashierId(cashierId);
        bill.setStatus(10);
        try {
            billMapper.insert(bill);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发收费撞 visit_id 唯一索引：预检查窗口兜底，语义化提示
            throw new BizException(ErrorCode.B3001);
        }

        Set<Long> rxIds = new HashSet<>();
        Set<Long> applyIds = new HashSet<>();
        Long regId = null;
        for (PayableResultDTO.PayableItem item : items) {
            BilChargeDetail detail = new BilChargeDetail();
            detail.setBillId(bill.getId());
            detail.setVisitId(visit.getId());
            detail.setPatientId(visit.getPatientId());
            detail.setFeeType(item.getFeeType());
            detail.setSourceType(item.getSourceType());
            detail.setSourceDetailId(item.getSourceDetailId());
            detail.setItemName(item.getItemName());
            detail.setQuantity(item.getQuantity());
            detail.setUnitPrice(item.getUnitPrice());
            detail.setAmount(item.getAmount());
            detail.setRefundStatus(0);
            detail.setStatus(1);
            chargeDetailMapper.insert(detail);
            if (item.getSourceType() == 1) {
                regId = item.getSourceDetailId();
            } else if (item.getSourceType() == 2) {
                // sourceDetailId 为处方明细行，需回溯处方
            } else if (item.getSourceType() == 3) {
                applyIds.add(item.getSourceDetailId());
            }
        }
        // 处方级标记：按明细回溯处方 ID
        List<Long> rxItemIds = items.stream()
                .filter(i -> i.getSourceType() == 2)
                .map(PayableResultDTO.PayableItem::getSourceDetailId).toList();
        if (!rxItemIds.isEmpty()) {
            rxIds.addAll(clinicAppService.prescriptionIdByItemIds(rxItemIds).values());
        }
        registrationAppService.markCharged(regId);
        clinicAppService.markPrescriptionsCharged(rxIds);
        clinicAppService.markExamApplicationsCharged(applyIds);

        BilPaymentRecord payment = new BilPaymentRecord();
        payment.setBillId(bill.getId());
        payment.setPayNo(idGenerator.next("ZF"));
        payment.setPayMethod(req.getPayMethod());
        payment.setAmount(bill.getTotalAmount());
        payment.setTransactionId("MOCK-" + payment.getPayNo());
        payment.setPayStatus(1);
        payment.setPayTime(now);
        payment.setCashierId(cashierId);
        payment.setStatus(1);
        paymentRecordMapper.insert(payment);

        // 四期二批：门诊检查 RIS 联动（billing → ris 单向 hook，同事务；ris 内部消化异常）
        try {
            risAppService.createOutpatientRequests(bill.getVisitId());
        } catch (Exception e) {
            pltService.recordEvent("ris.outpatient.hook.failed", String.valueOf(bill.getVisitId()), "{}");
        }

        BillResponse resp = new BillResponse();
        resp.setId(bill.getId());
        resp.setBillNo(bill.getBillNo());
        resp.setVisitId(bill.getVisitId());
        resp.setPatientId(bill.getPatientId());
        resp.setTotalAmount(bill.getTotalAmount());
        resp.setPayableAmount(bill.getPayableAmount());
        resp.setPayMethod(bill.getPayMethod());
        resp.setStatus(bill.getStatus());
        return resp;
    }

    /** 退费（B-05，《05》R8~R10：明细级可退校验、已发药先退药、完成后挂号费不可退、联动作废） */
    @Transactional
    public String refund(RefundRequest req) {
        // 行锁序列化同一账单的并发退费（《05》R8~R10 一致性保障）
        BilChargeBill bill = billMapper.selectByIdForUpdate(req.getBillId());
        if (bill == null) {
            throw new BizException(ErrorCode.B3007);
        }
        // 数据范围（《04》§4）：收费员仅能退本人经办账单；管理员豁免（对齐 billPage）
        com.his.infrastructure.security.LoginUser refundUser = com.his.infrastructure.security.CurrentUser.get();
        if (!refundUser.getRoleCodes().contains("ADMIN")
                && !bill.getCashierId().equals(refundUser.getUserId())) {
            throw new BizException(ErrorCode.A0003, "仅能退本人经办的账单");
        }
        BillingVisitDTO visit = clinicAppService.getVisitForBilling(bill.getVisitId());
        List<BilChargeDetail> billDetails = chargeDetailMapper.selectList(
                new LambdaQueryWrapper<BilChargeDetail>().eq(BilChargeDetail::getBillId, bill.getId()));
        Map<Long, BilChargeDetail> detailById = billDetails.stream()
                .collect(java.util.stream.Collectors.toMap(BilChargeDetail::getId, d -> d));

        // 已退数量汇总
        Map<Long, BigDecimal> refundedQty = new HashMap<>();
        List<Long> allDetailIds = billDetails.stream().map(BilChargeDetail::getId).toList();
        if (!allDetailIds.isEmpty()) {
            for (BilRefundDetail rd : refundDetailMapper.selectList(new LambdaQueryWrapper<BilRefundDetail>()
                    .in(BilRefundDetail::getChargeDetailId, allDetailIds))) {
                refundedQty.merge(rd.getChargeDetailId(), rd.getRefundQuantity(), BigDecimal::add);
            }
        }

        // 药品费明细 → 处方状态（B3004：已发药须先退药）
        Map<Long, Integer> rxStatusByItemId = clinicAppService.prescriptionStatusByItemIds(
                billDetails.stream()
                        .filter(d -> d.getSourceType() == 2 && d.getFeeType() == 7)
                        .map(BilChargeDetail::getSourceDetailId).toList());

        // 药品费必须整方退（《05》R8/R9 补充：部分退费会导致处方仍可发药的不一致）
        Map<Long, BigDecimal> requestedQtyByDetail = new HashMap<>();
        for (RefundRequest.Line line : req.getDetails()) {
            requestedQtyByDetail.merge(line.getChargeDetailId(), line.getRefundQuantity(), BigDecimal::add);
        }
        Map<Long, Long> rxIdByItem = clinicAppService.prescriptionIdByItemIds(
                billDetails.stream()
                        .filter(d -> d.getSourceType() == 2 && d.getFeeType() == 7)
                        .map(BilChargeDetail::getSourceDetailId).toList());
        Map<Long, List<BilChargeDetail>> rxGroups = new LinkedHashMap<>();
        for (BilChargeDetail d : billDetails) {
            if (d.getSourceType() == 2 && d.getFeeType() == 7 && rxIdByItem.containsKey(d.getSourceDetailId())) {
                rxGroups.computeIfAbsent(rxIdByItem.get(d.getSourceDetailId()), k -> new ArrayList<>()).add(d);
            }
        }
        for (Map.Entry<Long, List<BilChargeDetail>> group : rxGroups.entrySet()) {
            boolean allFull = group.getValue().stream().allMatch(d -> {
                BigDecimal refunded = refundedQty.getOrDefault(d.getId(), BigDecimal.ZERO)
                        .add(requestedQtyByDetail.getOrDefault(d.getId(), BigDecimal.ZERO));
                return refunded.compareTo(d.getQuantity()) >= 0;
            });
            if (!allFull) {
                throw new BizException(ErrorCode.B3003, "处方药品费须整方退费，请勾选该处方全部明细");
            }
        }

        BigDecimal refundTotal = BigDecimal.ZERO;
        List<BilRefundDetail> refundDetails = new ArrayList<>();
        for (RefundRequest.Line line : req.getDetails()) {
            BilChargeDetail detail = detailById.get(line.getChargeDetailId());
            if (detail == null || detail.getStatus() == 0) {
                throw new BizException(ErrorCode.A0001, "费用明细不存在: " + line.getChargeDetailId());
            }
            BigDecimal qty = line.getRefundQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.B3003, "退费数量必须大于 0");
            }
            BigDecimal available = detail.getQuantity().subtract(refundedQty.getOrDefault(detail.getId(), BigDecimal.ZERO));
            if (qty.compareTo(available) > 0) {
                throw new BizException(ErrorCode.B3003,
                        "退费数量超过可退数量: " + detail.getItemName() + "（可退 " + available + "）");
            }
            if (detail.getFeeType() == 7) {
                Integer rxStatus = rxStatusByItemId.get(detail.getSourceDetailId());
                if (rxStatus != null && rxStatus == 30) {
                    throw new BizException(ErrorCode.B3004, detail.getItemName() + " 已发药，请先到药房退药");
                }
            }
            if ((detail.getFeeType() == 1 || detail.getFeeType() == 2)
                    && visit != null && visit.getVisitStatus() == 30) {
                throw new BizException(ErrorCode.B3005);
            }
            BigDecimal amount = detail.getUnitPrice().multiply(qty).setScale(2, RoundingMode.HALF_UP);
            refundTotal = refundTotal.add(amount);
            BilRefundDetail rd = new BilRefundDetail();
            rd.setChargeDetailId(detail.getId());
            rd.setRefundQuantity(qty);
            rd.setRefundAmount(amount);
            rd.setStatus(1);
            refundDetails.add(rd);
        }

        BilRefundBill refundBill = new BilRefundBill();
        refundBill.setRefundNo(idGenerator.next("TF"));
        refundBill.setBillId(bill.getId());
        refundBill.setVisitId(bill.getVisitId());
        refundBill.setPatientId(bill.getPatientId());
        refundBill.setRefundAmount(refundTotal);
        refundBill.setReason(req.getReason());
        refundBill.setRefundMethod(2);
        refundBill.setRefundTime(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        refundBill.setOperatorId(CurrentUser.id());
        refundBill.setStatus(10);
        refundBillMapper.insert(refundBill);
        for (BilRefundDetail rd : refundDetails) {
            rd.setRefundBillId(refundBill.getId());
            refundDetailMapper.insert(rd);
            BilChargeDetail detail = detailById.get(rd.getChargeDetailId());
            BigDecimal refunded = refundedQty.getOrDefault(detail.getId(), BigDecimal.ZERO).add(rd.getRefundQuantity());
            detail.setRefundStatus(refunded.compareTo(detail.getQuantity()) >= 0 ? 2 : 1);
            chargeDetailMapper.updateById(detail);
        }

        // 更新收费单累计退费与状态（R10）
        BigDecimal newRefund = bill.getRefundAmount().add(refundTotal);
        bill.setRefundAmount(newRefund);
        if (bill.getPayableAmount().subtract(newRefund).compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(30);
        } else {
            bill.setStatus(20);
        }
        if (billMapper.updateById(bill) != 1) {
            throw new BizException(ErrorCode.C9001, "操作冲突，请刷新后重试");
        }

        // R9 联动：整方费用全退 → 作废处方；挂号费全退且未就诊 → 挂号单置已退号
        linkAfterRefund(bill, visit, detailById);
        return refundBill.getRefundNo();
    }

    /** 日结（B-07）：按收费员+日期汇总并锁定，重复日结拒绝（B3006） */
    @Transactional
    public BilDailySettlement settle(SettlementRequest req) {
        Long cashierId = CurrentUser.id();
        Long exists = settlementMapper.selectCount(new LambdaQueryWrapper<BilDailySettlement>()
                .eq(BilDailySettlement::getSettleDate, req.getSettleDate())
                .eq(BilDailySettlement::getCashierId, cashierId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.B3006);
        }
        LocalDateTime start = req.getSettleDate().atStartOfDay();
        LocalDateTime end = req.getSettleDate().atTime(23, 59, 59);
        List<BilChargeBill> bills = billMapper.selectList(new LambdaQueryWrapper<BilChargeBill>()
                .eq(BilChargeBill::getCashierId, cashierId)
                .ge(BilChargeBill::getPayTime, start).le(BilChargeBill::getPayTime, end));
        List<BilRefundBill> refunds = refundBillMapper.selectList(new LambdaQueryWrapper<BilRefundBill>()
                .eq(BilRefundBill::getOperatorId, cashierId)
                .ge(BilRefundBill::getRefundTime, start).le(BilRefundBill::getRefundTime, end));
        BigDecimal chargeTotal = bills.stream().map(BilChargeBill::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundTotal = refunds.stream().map(BilRefundBill::getRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BilDailySettlement settlement = new BilDailySettlement();
        settlement.setSettlementNo(idGenerator.next("RJ"));
        settlement.setSettleDate(req.getSettleDate());
        settlement.setCashierId(cashierId);
        settlement.setBillCount(bills.size());
        settlement.setRefundCount(refunds.size());
        settlement.setTotalChargeAmount(chargeTotal);
        settlement.setTotalRefundAmount(refundTotal);
        settlement.setNetAmount(chargeTotal.subtract(refundTotal));
        settlement.setStatus(1);
        try {
            settlementMapper.insert(settlement);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发日结撞 uk_settle_date_cashier：预检查窗口兜底，语义化提示
            throw new BizException(ErrorCode.B3006);
        }
        return settlement;
    }

    /** 住院出院结算（I-12）：一日清未结费用 → 住院收费单 + 支付 + 联动，押金返回供抵扣提示 */
    @Transactional
    public BillResponse settleAdmission(Long admissionId, Integer payMethod) {
        var view = inpAppService.getAdmissionView(admissionId);
        var admission = view.getAdmission();
        if (admission.getStatus() == 30) {
            throw new BizException(ErrorCode.B3001, "该住院已结算");
        }
        if (admission.getStatus() != 20) {
            throw new BizException(ErrorCode.B6003, "住院尚未办理出院，不能结算");
        }
        if (billMapper.selectCount(new LambdaQueryWrapper<BilChargeBill>()
                .eq(BilChargeBill::getAdmissionId, admissionId)) > 0) {
            throw new BizException(ErrorCode.B3001);
        }
        List<com.his.modules.inp.app.DailyFeeDTO> fees = inpAppService.listUnpaidDailyFees(admissionId);
        if (fees.isEmpty()) {
            throw new BizException(ErrorCode.B3002);
        }
        Long cashierId = CurrentUser.id();
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        BigDecimal total = fees.stream().map(com.his.modules.inp.app.DailyFeeDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BilChargeBill bill = new BilChargeBill();
        bill.setBillNo(idGenerator.next("SF"));
        bill.setVisitId(null);
        bill.setAdmissionId(admissionId);
        bill.setPatientId(admission.getPatientId());
        bill.setTotalAmount(total);
        bill.setDiscountAmount(BigDecimal.ZERO);
        bill.setPayableAmount(total);
        bill.setPaidAmount(total);
        bill.setRefundAmount(BigDecimal.ZERO);
        bill.setPayMethod(payMethod);
        bill.setPayTime(now);
        bill.setCashierId(cashierId);
        bill.setStatus(10);
        try {
            billMapper.insert(bill);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.B3001);
        }
        for (var fee : fees) {
            BilChargeDetail detail = new BilChargeDetail();
            detail.setBillId(bill.getId());
            detail.setVisitId(null);
            detail.setAdmissionId(admissionId);
            detail.setPatientId(admission.getPatientId());
            detail.setFeeType(fee.getFeeType());
            detail.setSourceType(4); // 4 住院日费用（《09》source_type 扩展）
            detail.setSourceDetailId(fee.getId());
            detail.setItemName(fee.getItemName());
            detail.setQuantity(fee.getQuantity());
            detail.setUnitPrice(fee.getUnitPrice());
            detail.setAmount(fee.getAmount());
            detail.setRefundStatus(0);
            detail.setStatus(1);
            chargeDetailMapper.insert(detail);
        }
        BilPaymentRecord payment = new BilPaymentRecord();
        payment.setBillId(bill.getId());
        payment.setPayNo(idGenerator.next("ZF"));
        payment.setPayMethod(payMethod);
        payment.setAmount(total);
        payment.setTransactionId("MOCK-" + payment.getPayNo());
        payment.setPayStatus(1);
        payment.setPayTime(now);
        payment.setCashierId(cashierId);
        payment.setStatus(1);
        paymentRecordMapper.insert(payment);

        inpAppService.markDailyFeesSettled(admissionId);
        inpAppService.markSettled(admissionId);
        pltService.recordEvent("bill.admission.settled", bill.getBillNo(),
                "{\"admissionId\":" + admissionId + ",\"total\":" + total + "}");

        BillResponse resp = new BillResponse();
        resp.setId(bill.getId());
        resp.setBillNo(bill.getBillNo());
        resp.setAdmissionId(admissionId);
        resp.setPatientId(admission.getPatientId());
        resp.setTotalAmount(bill.getTotalAmount());
        resp.setPayableAmount(bill.getPayableAmount());
        resp.setPayMethod(bill.getPayMethod());
        resp.setStatus(bill.getStatus());
        return resp;
    }

    // ---------------- 查询 ----------------

    public PageResult<BillResponse> billPage(BillQuery query) {
        // 数据范围（《04》§4）：收费员仅本人经办；管理员/对账员全量
        com.his.infrastructure.security.LoginUser user = com.his.infrastructure.security.CurrentUser.get();
        if (!user.getRoleCodes().contains("ADMIN") && !user.getRoleCodes().contains("AUDITOR")) {
            query.setCashierId(user.getUserId());
        }
        Page<BilChargeBill> page = billMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<BilChargeBill>()
                        .like(query.getBillNo() != null && !query.getBillNo().isBlank(), BilChargeBill::getBillNo, query.getBillNo())
                        .eq(query.getPatientId() != null, BilChargeBill::getPatientId, query.getPatientId())
                        .eq(query.getCashierId() != null, BilChargeBill::getCashierId, query.getCashierId())
                        .ge(query.getStartDate() != null, BilChargeBill::getPayTime, query.getStartDate() == null ? null : query.getStartDate().atStartOfDay())
                        .le(query.getEndDate() != null, BilChargeBill::getPayTime, query.getEndDate() == null ? null : query.getEndDate().atTime(23, 59, 59))
                        .orderByDesc(BilChargeBill::getId));
        Map<Long, String> cashierNames = systemAppService.getUsernameMap(
                page.getRecords().stream().map(BilChargeBill::getCashierId).toList());
        return PageResult.of(page, bill -> {
            BillResponse resp = new BillResponse();
            resp.setId(bill.getId());
            resp.setBillNo(bill.getBillNo());
            resp.setVisitId(bill.getVisitId());
            resp.setAdmissionId(bill.getAdmissionId());
            resp.setPatientId(bill.getPatientId());
            var patient = patientAppService.getById(bill.getPatientId());
            resp.setPatientName(patient == null ? null : patient.getName());
            resp.setTotalAmount(bill.getTotalAmount());
            resp.setDiscountAmount(bill.getDiscountAmount());
            resp.setPayableAmount(bill.getPayableAmount());
            resp.setPaidAmount(bill.getPaidAmount());
            resp.setRefundAmount(bill.getRefundAmount());
            resp.setPayMethod(bill.getPayMethod());
            resp.setPayTime(bill.getPayTime());
            resp.setCashierId(bill.getCashierId());
            resp.setCashierName(cashierNames.get(bill.getCashierId()));
            resp.setStatus(bill.getStatus());
            return resp;
        });
    }

    public BillResponse billDetail(Long billId) {
        BilChargeBill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.B3007);
        }
        BillResponse resp = new BillResponse();
        resp.setId(bill.getId());
        resp.setBillNo(bill.getBillNo());
        resp.setVisitId(bill.getVisitId());
        resp.setPatientId(bill.getPatientId());
        var patient = patientAppService.getById(bill.getPatientId());
        resp.setPatientName(patient == null ? null : patient.getName());
        resp.setTotalAmount(bill.getTotalAmount());
        resp.setDiscountAmount(bill.getDiscountAmount());
        resp.setPayableAmount(bill.getPayableAmount());
        resp.setPaidAmount(bill.getPaidAmount());
        resp.setRefundAmount(bill.getRefundAmount());
        resp.setPayMethod(bill.getPayMethod());
        resp.setPayTime(bill.getPayTime());
        resp.setCashierId(bill.getCashierId());
        resp.setCashierName(systemAppService.getUsername(bill.getCashierId()));
        resp.setStatus(bill.getStatus());
        BillingVisitDTO visit = clinicAppService.getVisitForBilling(bill.getVisitId());
        if (visit != null) {
            resp.setVisitNo(visit.getVisitNo());
        }
        resp.setDetails(chargeDetailMapper.selectList(new LambdaQueryWrapper<BilChargeDetail>()
                        .eq(BilChargeDetail::getBillId, billId).orderByAsc(BilChargeDetail::getId))
                .stream().map(d -> {
                    BillResponse.Detail item = new BillResponse.Detail();
                    item.setId(d.getId());
                    item.setFeeType(d.getFeeType());
                    item.setSourceType(d.getSourceType());
                    item.setSourceDetailId(d.getSourceDetailId());
                    item.setItemName(d.getItemName());
                    item.setQuantity(d.getQuantity());
                    item.setUnitPrice(d.getUnitPrice());
                    item.setAmount(d.getAmount());
                    item.setRefundStatus(d.getRefundStatus());
                    return item;
                }).toList());
        resp.setPayments(paymentRecordMapper.selectList(new LambdaQueryWrapper<BilPaymentRecord>()
                        .eq(BilPaymentRecord::getBillId, billId).orderByAsc(BilPaymentRecord::getId))
                .stream().map(p -> {
                    BillResponse.Payment item = new BillResponse.Payment();
                    item.setId(p.getId());
                    item.setPayNo(p.getPayNo());
                    item.setPayMethod(p.getPayMethod());
                    item.setAmount(p.getAmount());
                    item.setTransactionId(p.getTransactionId());
                    item.setPayTime(p.getPayTime());
                    return item;
                }).toList());
        Map<Long, String> operatorNames = new HashMap<>();
        List<BilRefundBill> refunds = refundBillMapper.selectList(new LambdaQueryWrapper<BilRefundBill>()
                .eq(BilRefundBill::getBillId, billId).orderByDesc(BilRefundBill::getId));
        refunds.forEach(r -> operatorNames.put(r.getOperatorId(), systemAppService.getUsername(r.getOperatorId())));
        resp.setRefunds(refunds.stream().map(r -> {
            BillResponse.Refund item = new BillResponse.Refund();
            item.setId(r.getId());
            item.setRefundNo(r.getRefundNo());
            item.setRefundAmount(r.getRefundAmount());
            item.setReason(r.getReason());
            item.setRefundTime(r.getRefundTime());
            item.setOperatorName(operatorNames.get(r.getOperatorId()));
            return item;
        }).toList());
        return resp;
    }

    public PageResult<BillResponse.Refund> refundPage(RefundQuery query) {
        // 数据范围：收费员仅本人经办退费
        com.his.infrastructure.security.LoginUser user = com.his.infrastructure.security.CurrentUser.get();
        if (!user.getRoleCodes().contains("ADMIN") && !user.getRoleCodes().contains("AUDITOR")) {
            query.setOperatorId(user.getUserId());
        }
        Page<BilRefundBill> page = refundBillMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<BilRefundBill>()
                        .like(query.getRefundNo() != null && !query.getRefundNo().isBlank(), BilRefundBill::getRefundNo, query.getRefundNo())
                        .eq(query.getBillId() != null, BilRefundBill::getBillId, query.getBillId())
                        .eq(query.getOperatorId() != null, BilRefundBill::getOperatorId, query.getOperatorId())
                        .ge(query.getStartDate() != null, BilRefundBill::getRefundTime, query.getStartDate() == null ? null : query.getStartDate().atStartOfDay())
                        .le(query.getEndDate() != null, BilRefundBill::getRefundTime, query.getEndDate() == null ? null : query.getEndDate().atTime(23, 59, 59))
                        .orderByDesc(BilRefundBill::getId));
        Map<Long, String> operatorNames = systemAppService.getUsernameMap(
                page.getRecords().stream().map(BilRefundBill::getOperatorId).toList());
        return PageResult.of(page, r -> {
            BillResponse.Refund item = new BillResponse.Refund();
            item.setId(r.getId());
            item.setRefundNo(r.getRefundNo());
            item.setRefundAmount(r.getRefundAmount());
            item.setReason(r.getReason());
            item.setRefundTime(r.getRefundTime());
            item.setOperatorName(operatorNames.get(r.getOperatorId()));
            return item;
        });
    }

    public PageResult<BilDailySettlement> settlementPage(SettlementQuery query) {
        // 数据范围：收费员仅本人日结
        com.his.infrastructure.security.LoginUser user = com.his.infrastructure.security.CurrentUser.get();
        if (!user.getRoleCodes().contains("ADMIN") && !user.getRoleCodes().contains("AUDITOR")) {
            query.setCashierId(user.getUserId());
        }
        Page<BilDailySettlement> page = settlementMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<BilDailySettlement>()
                        .eq(query.getSettleDate() != null, BilDailySettlement::getSettleDate, query.getSettleDate())
                        .eq(query.getCashierId() != null, BilDailySettlement::getCashierId, query.getCashierId())
                        .orderByDesc(BilDailySettlement::getId));
        return PageResult.of(page);
    }

    // ---------------- 内部方法 ----------------

    private List<PayableResultDTO.PayableItem> buildPayableItems(BillingVisitDTO visit) {
        List<PayableResultDTO.PayableItem> items = new ArrayList<>();
        RegistrationDTO reg = registrationAppService.getById(visit.getRegistrationId());
        if (reg != null && reg.getChargeStatus() == 0 && (reg.getStatus() == 10 || reg.getStatus() == 30)) {
            items.add(payableItem(1, 1, reg.getId(), "挂号费", BigDecimal.ONE, reg.getRegFee()));
            items.add(payableItem(1, 2, reg.getId(), "诊查费", BigDecimal.ONE, reg.getConsultationFee()));
        }
        for (BillingRxItemDTO rx : clinicAppService.listUnpaidPrescriptionItems(visit.getId())) {
            items.add(payableItem(2, 7, rx.getPrescriptionItemId(),
                    rx.getDrugName() + (rx.getRxNo() == null ? "" : "（" + rx.getRxNo() + "）"),
                    rx.getQuantity(), rx.getUnitPrice()));
        }
        for (com.his.modules.clinic.app.BillingExamDTO exam : clinicAppService.listUnpaidExamApplications(visit.getId())) {
            int feeType = exam.getCategory() != null && exam.getCategory() == 3 ? 3 : 4;
            items.add(payableItem(3, feeType, exam.getExamId(), exam.getItemName(), BigDecimal.ONE, exam.getPrice()));
        }
        return items;
    }

    private PayableResultDTO.PayableItem payableItem(int sourceType, int feeType, Long sourceDetailId,
                                                     String itemName, BigDecimal qty, BigDecimal price) {
        PayableResultDTO.PayableItem item = new PayableResultDTO.PayableItem();
        item.setSourceType(sourceType);
        item.setFeeType(feeType);
        item.setSourceDetailId(sourceDetailId);
        item.setItemName(itemName);
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setAmount(price.multiply(qty).setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    private void linkAfterRefund(BilChargeBill bill, BillingVisitDTO visit, Map<Long, BilChargeDetail> detailById) {
        // 整方药品费全退 → 作废处方（R9）
        Map<Long, List<BilChargeDetail>> rxDetails = new HashMap<>();
        for (BilChargeDetail d : detailById.values()) {
            if (d.getSourceType() == 2 && d.getFeeType() == 7) {
                Long rxId = clinicAppService.prescriptionIdByItemIds(List.of(d.getSourceDetailId()))
                        .get(d.getSourceDetailId());
                if (rxId != null) {
                    rxDetails.computeIfAbsent(rxId, k -> new ArrayList<>()).add(d);
                }
            }
        }
        for (Map.Entry<Long, List<BilChargeDetail>> entry : rxDetails.entrySet()) {
            boolean allFullyRefunded = entry.getValue().stream()
                    .allMatch(d -> d.getRefundStatus() != null && d.getRefundStatus() == 2);
            if (allFullyRefunded && !entry.getValue().isEmpty()) {
                try {
                    clinicAppService.voidPrescriptionForRefund(entry.getKey());
                } catch (BizException e) {
                    log.warn("退费联动作废处方失败 rxId={}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        // 挂号费+诊查费全退且未完成就诊 → 挂号单置已退号（R5/R9）
        List<BilChargeDetail> regDetails = detailById.values().stream()
                .filter(d -> d.getSourceType() == 1 && (d.getFeeType() == 1 || d.getFeeType() == 2)).toList();
        if (!regDetails.isEmpty() && visit != null && visit.getVisitStatus() != 30
                && regDetails.stream().allMatch(d -> d.getRefundStatus() != null && d.getRefundStatus() == 2)
                && visit.getRegistrationId() != null) {
            registrationAppService.cancelForRefund(visit.getRegistrationId());
        }
    }

    private BillingVisitDTO requireVisit(Long visitId) {
        BillingVisitDTO visit = clinicAppService.getVisitForBilling(visitId);
        if (visit == null) {
            throw new BizException(ErrorCode.A0001, "就诊不存在");
        }
        return visit;
    }
}
