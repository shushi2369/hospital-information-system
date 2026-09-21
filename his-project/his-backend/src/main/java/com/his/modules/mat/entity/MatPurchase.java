package com.his.modules.mat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code mat_purchase} */
@Getter
@Setter
@TableName("mat_purchase")
public class MatPurchase extends BaseEntity {
    private String poNo;
    private Long supplierId;
    private Long materialId;
    private Integer quantity;
    private java.math.BigDecimal unitPrice;
    private java.time.LocalDate expectedDate;
    private Long approverId;
    private java.time.LocalDateTime approvedAt;
    private Integer status;

    @Version
    private Integer version;
}
