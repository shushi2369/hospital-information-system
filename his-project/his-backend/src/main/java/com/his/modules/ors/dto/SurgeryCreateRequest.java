package com.his.modules.ors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** OR-01 手术申请创建 */
@Getter
@Setter
public class SurgeryCreateRequest {
    @NotNull(message = "住院 ID 不能为空")
    private Long admissionId;
    @NotNull(message = "患者 ID 不能为空")
    private Long patientId;
    @NotBlank(message = "手术名称不能为空")
    @jakarta.validation.constraints.Size(max = 64, message = "手术名称过长")
    private String surgeryName;
    @jakarta.validation.constraints.Size(max = 32, message = "术式编码过长")
    private String surgeryCode;
    @NotBlank(message = "术前诊断不能为空")
    @jakarta.validation.constraints.Size(max = 256, message = "术前诊断过长")
    @Size(max = 256)
    private String diagnosis;
    @NotNull(message = "拟手术日期不能为空")
    private LocalDate plannedDate;
    @NotNull(message = "麻醉方式不能为空")
    private Integer anesthesiaMethod;
    private Long surgeryItemId;
    private Long anesthesiaItemId;
    private BigDecimal surgeryPrice;
    private BigDecimal anesthesiaPrice;
}
