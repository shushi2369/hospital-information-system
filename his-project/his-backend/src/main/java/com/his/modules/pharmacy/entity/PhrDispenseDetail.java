package com.his.modules.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 发药明细（支持 FEFO 拆批发药）。 */
@Getter
@Setter
@TableName("phr_dispense_detail")
public class PhrDispenseDetail extends BaseEntity {
    private Long dispenseOrderId;
    private Long prescriptionItemId;
    private Long drugId;
    private Long batchId;
    private BigDecimal quantity;
    private Integer status;

    @Version
    private Integer version;
}
