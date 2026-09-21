package com.his.modules.pub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 疾控回执登记 */
@Getter
@Setter
public class ReceiptRequest {
    @NotBlank(message = "回执号不能为空")
    private String receiptNo;
}
