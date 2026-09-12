package com.his.modules.billing.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 收入明细行（收费明细/退费单，T-04 下钻）。 */
@Getter
@Setter
public class RevenueDetailRowDTO {
    private LocalDateTime time;
    private String docNo;
    private Integer type;
    private Long patientId;
    private String patientName;
    private String itemName;
    private Integer feeType;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
