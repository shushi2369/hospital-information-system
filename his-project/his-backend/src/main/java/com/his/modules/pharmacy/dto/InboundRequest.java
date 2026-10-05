package com.his.modules.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class InboundRequest {
    @NotNull(message = "药品不能为空")
    private Long drugId;
    @NotBlank(message = "生产批号不能为空")
    @Size(max = 32, message = "批号最长 32 位")
    private String batchNo;
    @NotNull(message = "失效日期不能为空")
    private LocalDate expiryDate;
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.01", message = "入库数量必须大于 0")
    private BigDecimal quantity;
    @DecimalMin(value = "0", message = "入库单价不能为负")
    private BigDecimal unitPrice;
    @Size(max = 64, message = "供应商最长 64 字")
    private String supplier;
}
