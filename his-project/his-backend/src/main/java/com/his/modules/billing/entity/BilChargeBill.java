package com.his.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收费单（每就诊一张，visit_id 唯一；状态 10已支付 20部分退费 30全额退费）。
 */
@Getter
@Setter
@TableName("bil_charge_bill")
public class BilChargeBill extends BaseEntity {
    private String billNo;
    private Long visitId;
    private Long admissionId;
    private Long patientId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;
    private Integer payMethod;
    private LocalDateTime payTime;
    private Long cashierId;
    private Integer status;

    @Version
    private Integer version;
}
