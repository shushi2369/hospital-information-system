package com.his.modules.pharmacy.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 库存预警/汇总行。 */
@Getter
@Setter
public class WarningDTO {
    private Long drugId;
    private String drugName;
    private BigDecimal totalQuantity;
    private BigDecimal stockWarningQty;
    private Boolean warning;
    private List<BatchVO> batches;
}
