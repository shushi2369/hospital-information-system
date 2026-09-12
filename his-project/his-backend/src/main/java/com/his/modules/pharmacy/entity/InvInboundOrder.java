package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 入库单（一品一行；同药品同批号累加库存批次）。 */
@Getter
@Setter
@TableName("inv_inbound_order")
public class InvInboundOrder extends BaseEntity {
    private String inboundNo;
    private Long drugId;
    private String batchNo;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String supplier;
    private Long operatorId;
    private Integer status;

    @Version
    private Integer version;
}
