package com.his.modules.mat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** M-04 采购单创建 */
@Getter
@Setter
public class MatPurchaseRequest {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    @NotNull(message = "物资不能为空")
    private Long materialId;
    @NotNull(message = "数量不能为空")
    @Min(1) @Max(100000)
    private Integer quantity;
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;
    private LocalDate expectedDate;
}
