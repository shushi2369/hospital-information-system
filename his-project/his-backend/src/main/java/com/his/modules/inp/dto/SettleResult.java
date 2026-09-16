package com.his.modules.inp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 出院结算结果。 */
@Getter
@Setter
public class SettleResult {
    private String billNo;
    private BigDecimal totalAmount;
    private BigDecimal depositTotal;
}
