package com.his.modules.nur.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ScheduleRequest {
    @NotNull(message = "护士不能为空")
    private Long nurseId;
    @NotNull(message = "病区不能为空")
    private Long wardId;
    @NotNull(message = "排班日期不能为空")
    private LocalDate shiftDate;
    @NotNull(message = "班次不能为空")
    @Min(value = 1, message = "班次取值 1白班/2中班/3夜班")
    @Max(value = 3, message = "班次取值 1白班/2中班/3夜班")
    private Integer shiftType;
}
