package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费视角的未收费检查/检验申请。
 */
@Getter
@Setter
public class BillingExamDTO {
    private Long examId;
    private String applyNo;
    private String itemName;
    private Integer category;
    private BigDecimal price;
}
