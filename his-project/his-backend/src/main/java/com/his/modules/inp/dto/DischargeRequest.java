package com.his.modules.inp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DischargeRequest {
    @NotNull(message = "出院方式不能为空")
    @Min(value = 1, message = "出院方式取值 1治愈/2好转/3未愈/4死亡/5自动离院/6转院")
    @Max(value = 6, message = "出院方式取值 1治愈/2好转/3未愈/4死亡/5自动离院/6转院")
    private Integer dischargeWay;
    @NotNull(message = "出院诊断不能为空")
    @Size(max = 256, message = "出院诊断最长 256 字")
    private String dischargeDiagnosis;
}
