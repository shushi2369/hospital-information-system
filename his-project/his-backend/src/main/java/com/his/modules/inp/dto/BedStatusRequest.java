package com.his.modules.inp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BedStatusRequest {
    @NotNull(message = "床位状态不能为空")
    @Min(value = 1, message = "床位状态取值 1空闲/3预约/4停用")
    @Max(value = 4, message = "床位状态取值 1空闲/3预约/4停用")
    private Integer bedStatus;
}
