package com.his.modules.ris.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** R-08 报告审核（通过即发布；驳回回书写中） */
@Getter
@Setter
public class ReportReviewRequest {
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;
    private String reason;
}
