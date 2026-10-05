package com.his.modules.pe.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** P-01 套餐创建/更新 */
@Getter
@Setter
public class PePackageRequest {
    @NotBlank(message = "套餐名称不能为空")
    private String name;
    @NotNull(message = "套餐价不能为空")
    @DecimalMin(value = "0", message = "套餐价不能为负")
    private BigDecimal price;
    @NotNull(message = "项目不能为空")
    private List<PackageItem> items;

    @Getter
    @Setter
    public static class PackageItem {
        private Long chargeItemId;
        private String itemName;
        @DecimalMin(value = "0", message = "项目价不能为负")
        private BigDecimal price;
    }
}
