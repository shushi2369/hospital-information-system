package com.his.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录（一期模拟支付，transaction_id 模拟生成）。
 */
@Getter
@Setter
@TableName("bil_payment_record")
public class BilPaymentRecord extends BaseEntity {
    private Long billId;
    private String payNo;
    private Integer payMethod;
    private BigDecimal amount;
    private String transactionId;
    private Integer payStatus;
    private LocalDateTime payTime;
    private Long cashierId;
    private Integer status;

    @Version
    private Integer version;
}
