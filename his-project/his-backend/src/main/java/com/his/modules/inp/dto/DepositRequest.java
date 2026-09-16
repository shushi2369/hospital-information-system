package com.his.modules.inp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DepositRequest {
    @NotNull(message = "押金金额不能为空")
    @DecimalMin(value = "0.01", message = "押金金额必须大于 0")
    private BigDecimal amount;
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    @Max(value = 4, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    private Integer payMethod;
}
