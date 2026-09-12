package com.his.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退费单（状态 10已退费 20已驳回；金额 ≤ 原单可退余额）。
 */
@Getter
@Setter
@TableName("bil_refund_bill")
public class BilRefundBill extends BaseEntity {
    private String refundNo;
    private Long billId;
    private Long visitId;
    private Long patientId;
    private BigDecimal refundAmount;
    private String reason;
    private Integer refundMethod;
    private LocalDateTime refundTime;
    private Long operatorId;
    private Integer status;

    @Version
    private Integer version;
}
