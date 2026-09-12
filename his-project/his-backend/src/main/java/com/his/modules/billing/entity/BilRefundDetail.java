package com.his.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 退费明细（对应原费用明细行）。
 */
@Getter
@Setter
@TableName("bil_refund_detail")
public class BilRefundDetail extends BaseEntity {
    private Long refundBillId;
    private Long chargeDetailId;
    private BigDecimal refundQuantity;
    private BigDecimal refundAmount;
    private Integer status;

    @Version
    private Integer version;
}
