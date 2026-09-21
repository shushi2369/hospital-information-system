package com.his.modules.bb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** B-08 交叉配血登记 */
@Getter
@Setter
public class CrossMatchRequest {
    @NotNull(message = "血袋不能为空")
    private Long bagId;
    @NotBlank(message = "配血方法不能为空")
    @Size(max = 32, message = "配血方法过长")
    private String crossMethod;
    @NotNull(message = "配血结果不能为空")
    @Min(1) @Max(2)
    private Integer crossResult;
    @Size(max = 256, message = "备注过长")
    private String note;
}
