package com.his.modules.inp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BedCreateRequest {
    @NotNull(message = "病区不能为空")
    private Long wardId;
    @NotBlank(message = "床号不能为空")
    @Size(max = 16, message = "床号最长 16 位")
    private String bedNo;
    @NotNull(message = "床位费收费项目不能为空")
    private Long chargeItemId;
}
