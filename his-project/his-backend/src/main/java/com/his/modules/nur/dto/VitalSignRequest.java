package com.his.modules.nur.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 体征录入（超范围由服务层给出中文提示）。 */
@Getter
@Setter
public class VitalSignRequest {
    @NotNull(message = "住院不能为空")
    private Long admissionId;
    private LocalDateTime recordTime;
    @NotNull(message = "体温不能为空")
    private Double temperature;
    @NotNull(message = "脉搏不能为空")
    private Integer pulse;
    @NotNull(message = "呼吸不能为空")
    private Integer respiration;
    @NotNull(message = "收缩压不能为空")
    private Integer bpHigh;
    @NotNull(message = "舒张压不能为空")
    private Integer bpLow;
    private Integer spo2;
    private Integer painScore;
}
