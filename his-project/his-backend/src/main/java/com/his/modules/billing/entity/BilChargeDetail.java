package com.his.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 费用明细（来源：1挂号单 2处方明细 3检查申请；项目名/单价快照）。
 */
@Getter
@Setter
@TableName("bil_charge_detail")
public class BilChargeDetail extends BaseEntity {
    private Long billId;
    private Long visitId;
    private Long patientId;
    private Integer feeType;
    private Integer sourceType;
    private Long sourceDetailId;
    private String itemName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private Integer refundStatus;
    private Integer status;

    @Version
    private Integer version;
}
