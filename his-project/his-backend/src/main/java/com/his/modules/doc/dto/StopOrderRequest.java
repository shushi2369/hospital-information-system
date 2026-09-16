package com.his.modules.doc.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StopOrderRequest {
    @Size(max = 256, message = "停止原因最长 256 字")
    private String reason;
}
