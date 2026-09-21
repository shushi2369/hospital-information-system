package com.his.modules.bb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** B-12 不良反应登记 */
@Getter
@Setter
public class AdverseRequest {
    @NotNull(message = "反应类型不能为空")
    @Min(1) @Max(4)
    private Integer type;
    @NotNull(message = "严重程度不能为空")
    @Min(1) @Max(3)
    private Integer severity;
    @NotBlank(message = "处置记录不能为空")
    @Size(max = 512, message = "处置记录过长")
    private String handleNote;
}
