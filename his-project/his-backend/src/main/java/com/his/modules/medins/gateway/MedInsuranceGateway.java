package com.his.modules.medins.gateway;

import java.math.BigDecimal;

/**
 * 医保网关防腐层（对齐一期 PaymentGateway 模式）：二期 Mock 通道，
 * 本地医保 SDK 联调时仅替换实现（《08》§4 / 《11》§5）。
 */
public interface MedInsuranceGateway {

    /** 申报拆分：返回 [统筹支付, 个人账户支付, 自费] */
    ApplyResult apply(BigDecimal totalAmount, Integer insuranceType);

    /** 日对账：申报金额与账单一致返回 true */
    boolean reconcile(String settleNo, BigDecimal declaredAmount, BigDecimal billAmount);

    record ApplyResult(BigDecimal poolPay, BigDecimal accountPay, BigDecimal selfPay) {
    }
}
