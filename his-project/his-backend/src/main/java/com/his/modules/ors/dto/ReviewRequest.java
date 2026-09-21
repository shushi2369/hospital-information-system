package com.his.modules.ors.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** OR-04 手术审核 */
@Getter
@Setter
public class ReviewRequest {
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;
    private String reason;
}
