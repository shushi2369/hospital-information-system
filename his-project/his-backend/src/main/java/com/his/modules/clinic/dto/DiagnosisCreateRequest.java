package com.his.modules.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagnosisCreateRequest {
    @Size(max = 16, message = "ICD-10 编码最长 16 位")
    private String diagnosisCode;
    @NotBlank(message = "诊断名称不能为空")
    @Size(max = 64, message = "诊断名称最长 64 字")
    private String diagnosisName;
    private Integer diagnosisType;
}
