package com.his.modules.billing.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 待缴费清单（B-01，收费前预览）。
 */
@Getter
@Setter
public class PayableResultDTO {
    private Long visitId;
    private String visitNo;
    private String patientName;
    private List<PayableItem> items;
    private BigDecimal totalAmount;

    @Getter
    @Setter
    public static class PayableItem {
        private Integer sourceType;
        private Long sourceDetailId;
        private Integer feeType;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
    }
}
