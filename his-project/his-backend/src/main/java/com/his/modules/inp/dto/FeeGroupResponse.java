package com.his.modules.inp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 一日清（按日期分组）。 */
@Getter
@Setter
public class FeeGroupResponse {
    private LocalDate feeDate;
    private BigDecimal totalAmount;
    private List<FeeItem> items;

    @Getter
    @Setter
    public static class FeeItem {
        private Long id;
        private Integer feeType;
        private Integer sourceType;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
    }
}
