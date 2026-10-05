package com.his.modules.clinic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处方明细行（单价由后端按药品字典快照，前端不传价格——《01》§6.9）。
 */
@Getter
@Setter
public class PrescriptionItemRequest {
    @NotNull(message = "药品不能为空")
    private Long drugId;
    @NotBlank(message = "单次剂量不能为空")
    @Size(max = 32, message = "单次剂量最长 32 字")
    private String dosage;
    @NotBlank(message = "用药频次不能为空")
    @Size(max = 16, message = "用药频次最长 16 字")
    private String frequency;
    @NotBlank(message = "用法不能为空")
    @Size(max = 16, message = "用法最长 16 字")
    private String usageRoute;
    @NotNull(message = "用药天数不能为空")
    @Min(value = 1, message = "用药天数至少 1 天")
    private Integer days;
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.01", message = "数量必须大于 0")
    private BigDecimal quantity;
    @Size(max = 128, message = "用药嘱托最长 128 字")
    private String usageNote;
}
