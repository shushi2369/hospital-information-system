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
    @jakarta.validation.constraints.Size(max = 512, message = "术中用药摘要过长")
    private String drugNote;
    @jakarta.validation.constraints.Size(max = 512, message = "术中事件过长")
    private String eventNote;
    @jakarta.validation.constraints.Size(max = 1024, message = "生命体征数据过长")
    private String vitalSample;
}
