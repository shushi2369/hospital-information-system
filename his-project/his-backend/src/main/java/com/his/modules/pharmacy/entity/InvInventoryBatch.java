package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 库存批次（条件更新保证 quantity >= 0；FEFO 拣批）。 */
@Getter
@Setter
@TableName("inv_inventory_batch")
public class InvInventoryBatch extends BaseEntity {
    private Long drugId;
    private String batchNo;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private BigDecimal initialQuantity;
    private Integer status;

    @Version
    private Integer version;
}
