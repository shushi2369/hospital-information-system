package com.his.modules.clinic.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 开方/检查申请成功返回（对齐前端契约）。
 */
@Getter
@Setter
public class PrescriptionCreateResult {
    private String rxNo;
    private BigDecimal totalAmount;

    public PrescriptionCreateResult(String rxNo, BigDecimal totalAmount) {
        this.rxNo = rxNo;
        this.totalAmount = totalAmount;
    }
}
