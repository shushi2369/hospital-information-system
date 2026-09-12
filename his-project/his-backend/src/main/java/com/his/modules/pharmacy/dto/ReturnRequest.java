package com.his.modules.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReturnRequest {
    @NotNull(message = "发药单不能为空")
    private Long dispenseOrderId;
    @NotBlank(message = "退药原因不能为空")
    @Size(max = 256, message = "退药原因最长 256 字")
    private String reason;
}
