package com.his.modules.bb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** B-11 输血结束 / B-12 不良反应 */
@Getter
@Setter
public class TransfusionFinishRequest {
    @NotNull(message = "转归不能为空")
    @Min(1) @Max(2)
    private Integer outcome;
    @Size(max = 256, message = "备注过长")
    private String note;
}
