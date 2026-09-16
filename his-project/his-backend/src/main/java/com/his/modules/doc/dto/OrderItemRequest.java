package com.his.modules.doc.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 医嘱明细行：药品类填 drugId，非药品类填 chargeItemId。 */
@Getter
@Setter
public class OrderItemRequest {
    private Long drugId;
    private Long chargeItemId;
    private String dosage;
    @Min(value = 1, message = "用药天数至少 1 天")
    private Integer days;
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.01", message = "数量必须大于 0")
    private BigDecimal quantity;
    private String usageRoute;
    private String usageNote;
}
