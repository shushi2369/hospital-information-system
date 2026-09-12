package com.his.modules.pharmacy.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 发药单视图。 */
@Getter
@Setter
public class DispenseOrderVO {
    private Long id;
    private String dispenseNo;
    private String rxNo;
    private Long prescriptionId;
    private String patientName;
    private String dispenserName;
    private BigDecimal totalQuantity;
    private LocalDateTime dispenseTime;
}
