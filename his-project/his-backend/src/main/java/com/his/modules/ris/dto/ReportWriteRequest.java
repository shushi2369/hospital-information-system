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
    private String finding;
    @NotBlank(message = "诊断意见不能为空")
    private String conclusion;
    private String criticalSign;
}
