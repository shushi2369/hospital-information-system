package com.his.modules.inp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 住院每日费用（一日清，计费落点）。 */
@Getter
@Setter
@TableName("inp_daily_fee")
public class InpDailyFee extends BaseEntity {
    private Long admissionId;
    private java.time.LocalDate feeDate;
    private Integer feeType;
    private Integer sourceType;
    private Long sourceDetailId;
    private String itemName;
    private java.math.BigDecimal quantity;
    private java.math.BigDecimal unitPrice;
    private java.math.BigDecimal amount;
    private Integer chargeStatus;
    private Integer status;
    @Version
    private Integer version;
}
