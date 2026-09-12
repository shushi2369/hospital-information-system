package com.his.modules.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class RefundRequest {
    @NotNull(message = "收费单不能为空")
    private Long billId;
    @NotBlank(message = "退费原因不能为空")
    @Size(max = 256, message = "退费原因最长 256 字")
    private String reason;
    @NotEmpty(message = "退费明细不能为空")
    @Valid
    private List<Line> details;

    @Getter
    @Setter
    public static class Line {
        @NotNull(message = "费用明细不能为空")
        private Long chargeDetailId;
        @NotNull(message = "退费数量不能为空")
        private BigDecimal refundQuantity;
    }
}
