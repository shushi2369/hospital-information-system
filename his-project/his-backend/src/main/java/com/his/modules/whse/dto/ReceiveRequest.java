package com.his.modules.whse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 到货入库：批号取采购单号，效期到货时录入。 */
@Getter
@Setter
public class ReceiveRequest {
    @NotNull(message = "失效日期不能为空")
    private LocalDate expiryDate;
}
