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
    /** 处方数字 id：手册/学生后续 review/dispense 都要用它（建号响应原来只有 rxNo，取 id 得再查列表） */
    private Long prescriptionId;
    private String rxNo;
    private BigDecimal totalAmount;

    public PrescriptionCreateResult(Long prescriptionId, String rxNo, BigDecimal totalAmount) {
        this.prescriptionId = prescriptionId;
        this.rxNo = rxNo;
        this.totalAmount = totalAmount;
    }
}
