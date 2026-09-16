package com.his.modules.medins.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mock 医保网关：职工统筹 70%/个账 10%/自费 20%；居民统筹 50%/个账 0/自费 50%。
 * 对账恒通过（金额由本系统计算，无外部差异源）。
 */
@Component
public class MockMedInsuranceGateway implements MedInsuranceGateway {

    @Override
    public ApplyResult apply(BigDecimal totalAmount, Integer insuranceType) {
        BigDecimal pool = totalAmount.multiply(insuranceType == 1
                ? new BigDecimal("0.70") : new BigDecimal("0.50"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal account = insuranceType == 1
                ? totalAmount.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new ApplyResult(pool, account, totalAmount.subtract(pool).subtract(account));
    }

    @Override
    public boolean reconcile(String settleNo, BigDecimal declaredAmount, BigDecimal billAmount) {
        return declaredAmount.compareTo(billAmount) == 0;
    }
}
