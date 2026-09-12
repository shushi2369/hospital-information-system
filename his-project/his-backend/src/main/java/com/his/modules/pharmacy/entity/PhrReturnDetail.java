package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 退药明细。 */
@Getter
@Setter
@TableName("phr_return_detail")
public class PhrReturnDetail extends BaseEntity {
    private Long returnOrderId;
    private Long prescriptionItemId;
    private Long drugId;
    private Long batchId;
    private BigDecimal quantity;
    private Integer status;

    @Version
    private Integer version;
}
