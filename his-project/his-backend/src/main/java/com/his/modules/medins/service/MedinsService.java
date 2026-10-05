package com.his.modules.medins.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.infrastructure.util.LikeEscapeUtil;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.billing.entity.BilChargeBill;
import com.his.modules.billing.mapper.BilChargeBillMapper;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.medins.dto.ReconcileRequest;
import com.his.modules.medins.dto.SettleQuery;
import com.his.modules.medins.entity.MedinsSettle;
import com.his.modules.medins.gateway.MedInsuranceGateway;
import com.his.modules.medins.mapper.MedinsSettleMapper;
import com.his.modules.plt.service.PltService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 医保结算服务（Y-01~Y-04）：出院结算后申报，Mock 网关拆分与对账。
 */
@Service
@RequiredArgsConstructor
public class MedinsService {
    private final MedinsSettleMapper settleMapper;
    private final BilChargeBillMapper billMapper;
    private final InpAppService inpAppService;
    private final MedInsuranceGateway gateway;
    private final com.his.modules.plt.service.PltService pltService;
    private final com.his.infrastructure.util.IdGenerator idGenerator;

    /** 申报（Y-01）：出院结算账单（已支付）+ 住院已结算 */
    @Transactional
    public String apply(Long billId, Integer insuranceType) {
        BilChargeBill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.B3007);
        }
        if (bill.getStatus() == 30) {
            // 部分退费(20)允许申报（按净额），全额退费不可申报
            throw new BizException(ErrorCode.B6402, "账单已全额退费，不可申报");
        }
        MedinsSettle existing = settleMapper.selectOne(new LambdaQueryWrapper<MedinsSettle>()
                .eq(MedinsSettle::getBillId, billId).last("LIMIT 1"));
        if (existing != null && existing.getStatus() != 30) {
            // 已申报/对账通过不可重复申报；仅"对账差异"允许调整后重新申报（《10》状态机 2.5）
            throw new BizException(ErrorCode.B6401);
        }
        var admission = inpAppService.requireAdmission(bill.getAdmissionId());
        // 申报按净额（应收 - 已退），防止退费后医保多付
        java.math.BigDecimal netAmount = bill.getPayableAmount().subtract(bill.getRefundAmount());
        if (netAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.B6402, "账单已全额退费，不可申报");
        }
        var split = gateway.apply(netAmount, insuranceType);
        MedinsSettle settle;
        if (existing != null) {
            settle = existing; // 差异重报：复用原申报单重算并回到待对账
        } else {
            settle = new MedinsSettle();
            settle.setSettleNo(idGenerator.next("YB"));
            settle.setBillId(billId);
            settle.setAdmissionId(admission.getId());
        }
        settle.setInsuranceType(insuranceType);
        settle.setTotalAmount(netAmount);
        settle.setPoolPay(split.poolPay());
        settle.setAccountPay(split.accountPay());
        settle.setSelfPay(split.selfPay());
        settle.setApplyTime(LocalDateTime.now());
        settle.setReconcileTime(null);
        settle.setDiffReason(null);
        settle.setOperatorId(CurrentUser.id());
        settle.setStatus(10);
        if (settle.getId() == null) {
            try {
                settleMapper.insert(settle);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BizException(ErrorCode.B6401);
            }
        } else {
            settleMapper.updateById(settle);
        }
        pltService.recordEvent("medins.settle.applied", settle.getSettleNo(),
                "{\"total\":" + settle.getTotalAmount() + "}");
        return settle.getSettleNo();
    }

    /** 对账（Y-03）：Mock 网关比对申报与账单金额 */
    @Transactional
    public void reconcile(Long settleId, ReconcileRequest req) {
        MedinsSettle settle = settleMapper.selectById(settleId);
        if (settle == null) {
            throw new BizException(ErrorCode.A0001, "申报单不存在");
        }
        if (settle.getStatus() != 10) {
            throw new BizException(ErrorCode.B6403, "该申报单不在待对账状态");
        }
        BilChargeBill bill = billMapper.selectById(settle.getBillId());
        BigDecimal netAmount = bill == null ? BigDecimal.ZERO
                : bill.getPayableAmount().subtract(bill.getRefundAmount());
        boolean pass = gateway.reconcile(settle.getSettleNo(), settle.getTotalAmount(), netAmount);
        settle.setStatus(pass ? 20 : 30);
        settle.setReconcileTime(LocalDateTime.now());
        settle.setDiffReason(pass ? null : "申报金额与账单不一致");
        settleMapper.updateById(settle);
        pltService.recordEvent("medins.settle.reconciled", settle.getSettleNo(),
                "{\"pass\":" + pass + "}");
    }

    /** 申报单分页（Y-02） */
    public PageResult<MedinsSettle> page(SettleQuery query) {
        Page<MedinsSettle> page = settleMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<MedinsSettle>()
                        .like(query.getSettleNo() != null && !query.getSettleNo().isBlank(),
                                MedinsSettle::getSettleNo, LikeEscapeUtil.escape(query.getSettleNo()))
                        .eq(query.getStatus() != null, MedinsSettle::getStatus, query.getStatus())
                        .orderByDesc(MedinsSettle::getId));
        return PageResult.of(page);
    }

    /** 日对账汇总（Y-04） */
    public List<MedinsSettle> dailyReconcile(java.time.LocalDate settleDate) {
        return settleMapper.selectList(new LambdaQueryWrapper<MedinsSettle>()
                .apply("DATE(apply_time) = {0}", settleDate)
                .orderByDesc(MedinsSettle::getId));
    }
}
