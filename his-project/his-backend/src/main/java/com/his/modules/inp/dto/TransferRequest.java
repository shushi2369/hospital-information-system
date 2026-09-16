package com.his.modules.inp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferRequest {
    @NotNull(message = "目标病区不能为空")
    private Long toWardId;
    @NotNull(message = "目标床位不能为空")
    private Long toBedId;
    @Size(max = 256, message = "转科原因最长 256 字")
    private String reason;
}
