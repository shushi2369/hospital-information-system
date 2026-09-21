package com.his.modules.mat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** M-09 科室领用 */
@Getter
@Setter
public class MatRequisitionRequest {
    @NotNull(message = "物资不能为空")
    private Long materialId;
    @NotNull(message = "领用科室不能为空")
    private Long deptId;
    @NotNull(message = "数量不能为空")
    @Min(1) @Max(10000)
    private Integer quantity;
    @Size(max = 128, message = "用途过长")
    private String purpose;
}
