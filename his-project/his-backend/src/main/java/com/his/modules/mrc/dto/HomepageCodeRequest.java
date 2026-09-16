package com.his.modules.mrc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HomepageCodeRequest {
    @NotBlank(message = "主要诊断编码不能为空")
    @Size(max = 16, message = "主要诊断编码最长 16 位")
    private String mainDiagnosisCode;
    @NotBlank(message = "主要诊断名称不能为空")
    @Size(max = 64, message = "主要诊断名称最长 64 字")
    private String mainDiagnosisName;
    private List<DiagnosisItem> otherDiagnoses;
    @Size(max = 16, message = "手术编码最长 16 位")
    private String operationCode;

    @Getter
    @Setter
    public static class DiagnosisItem {
        private String code;
        private String name;
    }
}
