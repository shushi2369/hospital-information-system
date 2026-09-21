package com.his.modules.ors.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** OR-05 排台 */
@Getter
@Setter
public class ScheduleRequest {
    @NotNull(message = "手术间不能为空")
    private Long roomId;
    @NotNull(message = "手术日期不能为空")
    private LocalDate surgeryDate;
    @NotNull(message = "台次不能为空")
    @Min(1) @Max(10)
    private Integer seqNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @NotNull(message = "主刀医生不能为空")
    private Long surgeonId;
    private Long anesthetistId;
    private Long circulatingNurseId;
    private Long scrubNurseId;
}
