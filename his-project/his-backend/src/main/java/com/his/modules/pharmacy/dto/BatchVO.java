package com.his.modules.pharmacy.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 库存批次视图。 */
@Getter
@Setter
public class BatchVO {
    private Long id;
    private Long drugId;
    private String drugName;
    private String batchNo;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private BigDecimal initialQuantity;
    private Integer status;

    /** 库存流水视图（F-11）。 */
    @Getter
    @Setter
    public static class Movement {
        private Long id;
        private Long drugId;
        private String drugName;
        private Integer movementType;
        private BigDecimal quantity;
        private BigDecimal beforeQty;
        private BigDecimal afterQty;
        private Integer refType;
        private String refNo;
        private LocalDateTime createdAt;
    }
}
