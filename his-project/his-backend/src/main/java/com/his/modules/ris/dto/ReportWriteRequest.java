package com.his.modules.ris.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** R-07 报告书写（已驳回报告可修改重提） */
@Getter
@Setter
public class ReportWriteRequest {
    @NotNull(message = "检查申请不能为空")
    private Long requestId;
    @NotBlank(message = "影像所见不能为空")
    @jakarta.validation.constraints.Size(max = 2048, message = "影像所见过长")
    private String finding;
    @NotBlank(message = "诊断意见不能为空")
    @jakarta.validation.constraints.Size(max = 1024, message = "诊断意见过长")
    private String conclusion;
    @jakarta.validation.constraints.Size(max = 128, message = "危急征象描述过长")
    private String criticalSign;
}
