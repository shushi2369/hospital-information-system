package com.his.modules.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 住院出院结算请求。 */
@Getter
@Setter
public class SettleAdmissionRequest {
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    @Max(value = 4, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    private Integer payMethod;
}
