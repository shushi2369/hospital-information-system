package com.his.modules.basedata.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 跨模块输出的收费项目视图（诊查费/检查费快照来源）。
 */
@Getter
@Setter
public class ChargeItemDTO {
    private Long id;
    private String itemCode;
    private String itemName;
    private Integer category;
    private BigDecimal price;
    private String unit;
    private Integer status;
}
