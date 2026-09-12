package com.his.modules.billing.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 日收入统计行。 */
@Getter
@Setter
public class DailyRevenueDTO {
    private LocalDate date;
    private BigDecimal chargeAmount;
    private BigDecimal refundAmount;
    private BigDecimal netAmount;

    /** 费用类别分布行。 */
    @Getter
    @Setter
    public static class FeeTypeAmount {
        private Integer feeType;
        private BigDecimal amount;
    }
}
