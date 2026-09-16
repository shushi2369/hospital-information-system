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
public class EmrTemplateRequest {
    @NotBlank(message = "模板名不能为空")
    @Size(max = 64, message = "模板名最长 64 字")
    private String templateName;
    @NotNull(message = "文书类型不能为空")
    @Min(value = 1, message = "文书类型取值 1~9")
    @Max(value = 9, message = "文书类型取值 1~9")
    private Integer docType;
    private Long deptId;
    @NotNull(message = "模板内容不能为空")
    private Map<String, Object> content;
}
