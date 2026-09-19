package com.his.modules.lis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ThresholdRequest {
    @NotBlank(message = "检验项目名不能为空")
    @Size(max = 64, message = "项目名最长 64 字")
    private String itemName;
    private BigDecimal lowValue;
    private BigDecimal highValue;
}
