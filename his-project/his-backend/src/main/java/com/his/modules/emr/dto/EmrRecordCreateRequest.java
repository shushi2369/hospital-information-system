package com.his.modules.emr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EmrRecordCreateRequest {
    @NotNull(message = "住院不能为空")
    private Long admissionId;
    @NotNull(message = "文书类型不能为空")
    @Min(value = 1, message = "文书类型取值 1入院记录/2病程记录/3出院记录/4知情同意/9其他")
    @Max(value = 9, message = "文书类型取值 1入院记录/2病程记录/3出院记录/4知情同意/9其他")
    private Integer docType;
    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长 64 字")
    private String title;
    @NotNull(message = "文书内容不能为空")
    private Map<String, Object> content;
}
