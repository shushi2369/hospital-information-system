package com.his.modules.emc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** E-01 分诊登记（患者从既有档案选择） */
@Getter
@Setter
public class TriageRequest {
    @NotNull(message = "患者不能为空")
    private Long patientId;
    @NotBlank(message = "主诉不能为空")
    @jakarta.validation.constraints.Size(max = 256, message = "主诉过长")
    private String chiefComplaint;
    private BigDecimal bodyTemp;
    private Integer pulse;
    private Integer respiration;
    @jakarta.validation.constraints.Size(max = 16, message = "血压格式过长")
    private String bloodPressure;
    private Integer spo2;
    @NotNull(message = "分诊级别不能为空")
    @Min(1) @Max(4)
    private Integer triageLevel;
    @Min(0) @Max(5)
    private Integer centerType;
    private Integer greenChannel;
}
