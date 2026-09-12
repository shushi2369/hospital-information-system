package com.his.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SettlementRequest {
    @NotNull(message = "日结日期不能为空")
    private LocalDate settleDate;
}
