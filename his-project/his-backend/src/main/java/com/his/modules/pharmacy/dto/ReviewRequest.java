package com.his.modules.pharmacy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequest {
    @NotNull(message = "审核结论不能为空")
    private Boolean pass;
    @Size(max = 256, message = "审核意见最长 256 字")
    private String comment;
}
