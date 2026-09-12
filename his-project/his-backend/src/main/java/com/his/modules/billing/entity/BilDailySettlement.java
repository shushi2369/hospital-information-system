package com.his.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日结（收费员+日期唯一；金额可由明细复核《05》R12）。
 */
@Getter
@Setter
@TableName("bil_daily_settlement")
public class BilDailySettlement extends BaseEntity {
    private String settlementNo;
    private LocalDate settleDate;
    private Long cashierId;
    private Integer billCount;
    private Integer refundCount;
    private BigDecimal totalChargeAmount;
    private BigDecimal totalRefundAmount;
    private BigDecimal netAmount;
    private Integer status;

    @Version
    private Integer version;
}
