package com.his.modules.pe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** P-08 总检报告发布 */
@Getter
@Setter
public class PeReportRequest {
    @NotBlank(message = "总检结论不能为空")
    @Size(max = 1024, message = "总检结论过长")
    private String summary;
}
