package com.his.modules.ors.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** OR-08 麻醉记录保存（术中可覆盖更新） */
@Getter
@Setter
public class AnesthesiaRequest {
    @NotNull(message = "ASA 分级不能为空")
    @Min(1) @Max(5)
    private Integer asaGrade;
    private Integer anesthesiaMethod;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String drugNote;
    private String eventNote;
    private String vitalSample;
}
