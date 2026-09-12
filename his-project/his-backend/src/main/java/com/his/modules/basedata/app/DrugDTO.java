package com.his.modules.basedata.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 跨模块输出的药品视图（开方快照来源）。
 */
@Getter
@Setter
public class DrugDTO {
    private Long id;
    private String drugCode;
    private String drugName;
    private String spec;
    private String unit;
    private BigDecimal retailPrice;
    private BigDecimal stockWarningQty;
    private Integer status;
}
