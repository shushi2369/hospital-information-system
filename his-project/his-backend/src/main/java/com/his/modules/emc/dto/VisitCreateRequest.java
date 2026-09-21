package com.his.modules.emc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** E-03 五大中心登记 */
@Getter
@Setter
public class VisitCreateRequest {
    @NotNull(message = "分诊单不能为空")
    private Long triageId;
    @NotNull(message = "中心类型不能为空")
    @Min(1) @Max(5)
    private Integer centerType;
    private Long doctorId;
}
