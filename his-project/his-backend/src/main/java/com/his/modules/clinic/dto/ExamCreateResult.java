package com.his.modules.clinic.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ExamCreateResult {
    private String applyNo;
    private BigDecimal price;

    public ExamCreateResult(String applyNo, BigDecimal price) {
        this.applyNo = applyNo;
        this.price = price;
    }
}
