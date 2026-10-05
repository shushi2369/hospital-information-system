package com.his.modules.doc.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 医嘱明细行：药品类填 drugId，非药品类填 chargeItemId。 */
@Getter
@Setter
public class OrderItemRequest {
    private Long drugId;
    private Long chargeItemId;
    @Size(max = 32, message = "单次剂量最长 32 字")
    private String dosage;
    @Min(value = 1, message = "用药天数至少 1 天")
    private Integer days;
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.01", message = "数量必须大于 0")
    private BigDecimal quantity;
    @Size(max = 16, message = "用法最长 16 字")
    private String usageRoute;
    @Size(max = 128, message = "用药嘱托最长 128 字")
    private String usageNote;
}
