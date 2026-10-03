package com.his.modules.inp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ManualFeeRequest {
    @NotNull(message = "费用类别不能为空")
    @Min(value = 1, message = "费用类别取值 1~8")
    @Max(value = 8, message = "费用类别取值 1~8")
    private Integer feeType;
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 64)
    private String itemName;
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.01", message = "数量必须大于 0")
    private BigDecimal quantity;
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.00", message = "单价不能为负")
    private BigDecimal unitPrice;
}
