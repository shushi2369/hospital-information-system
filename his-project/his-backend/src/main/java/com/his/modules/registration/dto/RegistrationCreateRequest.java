package com.his.modules.registration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RegistrationCreateRequest {
    @NotNull(message = "患者不能为空")
    private Long patientId;
    @NotNull(message = "医生不能为空")
    private Long doctorId;
    @NotNull(message = "就诊日期不能为空")
    private LocalDate regDate;
    @NotNull(message = "时段不能为空")
    @Min(value = 1, message = "时段取值 1 上午 / 2 下午")
    @Max(value = 2, message = "时段取值 1 上午 / 2 下午")
    private Integer period;
    @NotNull(message = "号别不能为空")
    @Min(value = 1, message = "号别取值 1 普通 / 2 专家")
    @Max(value = 2, message = "号别取值 1 普通 / 2 专家")
    private Integer regType;
}
