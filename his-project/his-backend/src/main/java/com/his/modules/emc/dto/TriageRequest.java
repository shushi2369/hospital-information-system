package com.his.modules.emc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 256)
    private String chiefComplaint;
    @jakarta.validation.constraints.DecimalMin(value = "30.0", message = "体温超出合理范围（30~45℃）")
    @jakarta.validation.constraints.DecimalMax(value = "45.0", message = "体温超出合理范围（30~45℃）")
    private BigDecimal bodyTemp;
    @Min(value = 20, message = "脉搏超出合理范围（20~300 次/分）")
    @Max(value = 300, message = "脉搏超出合理范围（20~300 次/分）")
    private Integer pulse;
    @Min(value = 5, message = "呼吸超出合理范围（5~80 次/分）")
    @Max(value = 80, message = "呼吸超出合理范围（5~80 次/分）")
    private Integer respiration;
    @jakarta.validation.constraints.Size(max = 16, message = "血压格式过长")
    private String bloodPressure;
    @Min(value = 50, message = "血氧饱和度超出合理范围（50~100%）")
    @Max(value = 100, message = "血氧饱和度超出合理范围（50~100%）")
    private Integer spo2;
    @NotNull(message = "分诊级别不能为空")
    @Min(1) @Max(4)
    private Integer triageLevel;
    @Min(0) @Max(5)
    private Integer centerType;
    private Integer greenChannel;
}
