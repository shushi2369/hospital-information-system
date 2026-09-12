package com.his.modules.registration.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 挂号成功返回（单号/排队号/费用快照）。
 */
@Getter
@Setter
public class RegistrationCreateResult {
    private String regNo;
    private Integer queueNo;
    private BigDecimal regFee;
    private BigDecimal consultationFee;
    private BigDecimal totalFee;

    public RegistrationCreateResult(String regNo, Integer queueNo, BigDecimal regFee,
                                    BigDecimal consultationFee, BigDecimal totalFee) {
        this.regNo = regNo;
        this.queueNo = queueNo;
        this.regFee = regFee;
        this.consultationFee = consultationFee;
        this.totalFee = totalFee;
    }
}
