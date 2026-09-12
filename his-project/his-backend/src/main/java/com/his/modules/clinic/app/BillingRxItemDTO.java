package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费视角的未收费处方明细行。
 */
@Getter
@Setter
public class BillingRxItemDTO {
    private Long prescriptionItemId;
    private Long prescriptionId;
    private String rxNo;
    private Long drugId;
    private String drugName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
