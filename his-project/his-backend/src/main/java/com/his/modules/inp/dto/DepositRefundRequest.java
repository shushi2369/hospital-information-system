package com.his.modules.inp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 退押金请求（七十轮）：仅已结算住院可退，上限 = 押金余额 - 结算账单额。
 */
@Getter
@Setter
public class DepositRefundRequest {
    @NotNull(message = "退押金金额不能为空")
    @DecimalMin(value = "0.01", message = "退押金金额必须大于 0")
    private BigDecimal amount;
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    private Integer payMethod;
    @Size(max = 128, message = "备注最长 128 位")
    private String reason;
}
