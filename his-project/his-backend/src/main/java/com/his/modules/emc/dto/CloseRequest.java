package com.his.modules.emc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** E-08 病例关档（转归） */
@Getter
@Setter
public class CloseRequest {
    @NotNull(message = "转归不能为空")
    @Min(1) @Max(5)
    private Integer outcome;
}
