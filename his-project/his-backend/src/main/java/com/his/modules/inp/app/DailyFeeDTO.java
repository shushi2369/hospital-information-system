package com.his.modules.inp.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 一日清费用跨模块视图（出院结算取数）。 */
@Getter
@Setter
public class DailyFeeDTO {
    private Long id;
    private LocalDate feeDate;
    private Integer feeType;
    private Integer sourceType;
    private Long sourceDetailId;
    private String itemName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
