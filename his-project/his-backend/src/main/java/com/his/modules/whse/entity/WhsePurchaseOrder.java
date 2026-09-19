package com.his.modules.whse.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code whse_purchase_order} */
@Getter
@Setter
@TableName("whse_purchase_order")
public class WhsePurchaseOrder extends BaseEntity {
    private String poNo;
    private Long supplierId;
    private Long drugId;
    private java.math.BigDecimal quantity;
    private java.math.BigDecimal unitPrice;
    private java.time.LocalDate expectedDate;
    private Integer status;
    private String inboundNo;
    private Long approverId;
    private java.time.LocalDateTime approvedAt;

    @Version
    private Integer version;
}
