package com.his.modules.ors.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** OR-10 离室登记（含术后记录） */
@Getter
@Setter
public class LeaveRequest {
    @NotNull(message = "苏醒评分不能为空")
    @Min(0) @Max(10)
    private Integer recoveryScore;
    @NotNull(message = "离室去向不能为空")
    @Min(1) @Max(3)
    private Integer destination;
    private String followupNote;
}
