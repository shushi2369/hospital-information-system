package com.his.modules.clinic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreateRequest {
    @NotNull(message = "医嘱类型不能为空")
    @Min(value = 1, message = "医嘱类型取值 1用药指导/2治疗/3复诊建议/9其他")
    @Max(value = 9, message = "医嘱类型取值 1用药指导/2治疗/3复诊建议/9其他")
    private Integer orderType;
    @NotBlank(message = "医嘱内容不能为空")
    @Size(max = 512, message = "医嘱内容最长 512 字")
    private String content;
}
