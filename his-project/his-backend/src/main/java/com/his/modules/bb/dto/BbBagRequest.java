package com.his.modules.bb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** B-01 血袋入库 */
@Getter
@Setter
public class BbBagRequest {
    @NotBlank(message = "血袋号不能为空")
    private String bagNo;
    @NotNull(message = "血型不能为空")
    @Min(1) @Max(4)
    private Integer bloodType;
    @NotNull(message = "Rh 不能为空")
    @Min(1) @Max(2)
    private Integer rh;
    @NotNull(message = "血液成分不能为空")
    @Min(1) @Max(4)
    private Integer component;
    private Integer volumeMl;
    private String bloodStation;
    private LocalDate collectDate;
    @NotNull(message = "失效日期不能为空")
    private LocalDate expireDate;
}
