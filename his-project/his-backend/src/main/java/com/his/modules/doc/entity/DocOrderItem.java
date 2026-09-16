package com.his.modules.doc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 医嘱明细（药品/收费项目二选一，快照同一期规范）。 */
@Getter
@Setter
@TableName("doc_order_item")
public class DocOrderItem extends BaseEntity {
    private Long orderId;
    private Long drugId;
    private Long chargeItemId;
    private String itemName;
    private String spec;
    private String dosage;
    private String frequency;
    private String usageRoute;
    private Integer days;
    private java.math.BigDecimal quantity;
    private String unit;
    private java.math.BigDecimal unitPrice;
    private java.math.BigDecimal amount;
    private String usageNote;
    private Integer status;
    @Version
    private Integer version;
}
