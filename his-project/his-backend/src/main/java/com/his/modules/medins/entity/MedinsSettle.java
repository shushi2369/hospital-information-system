package com.his.modules.medins.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 医保结算申报（Mock 网关；防腐层接口位预留本地 SDK）。 */
@Getter
@Setter
@TableName("medins_settle")
public class MedinsSettle extends BaseEntity {
    private String settleNo;
    private Long billId;
    private Long admissionId;
    private Integer insuranceType;
    private BigDecimal totalAmount;
    private BigDecimal accountPay;
    private BigDecimal poolPay;
    private BigDecimal selfPay;
    private LocalDateTime applyTime;
    private LocalDateTime reconcileTime;
    private String diffReason;
    private Long operatorId;
    private Integer status;

    @Version
    private Integer version;
}
