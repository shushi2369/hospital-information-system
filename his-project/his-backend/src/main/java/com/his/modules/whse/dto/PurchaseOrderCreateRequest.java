package com.his.modules.whse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PurchaseOrderCreateRequest {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotNull(message = "药品不能为空")
    private Long drugId;
    @NotNull(message = "采购数量不能为空")
    @DecimalMin(value = "0.01", message = "采购数量必须大于 0")
    private BigDecimal quantity;
    @NotNull(message = "采购单价不能为空")
    @DecimalMin(value = "0.01", message = "采购单价必须大于 0")
    private BigDecimal unitPrice;
    private LocalDate expectedDate;
}
