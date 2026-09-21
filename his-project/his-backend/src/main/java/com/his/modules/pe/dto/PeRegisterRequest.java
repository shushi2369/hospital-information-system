package com.his.modules.pe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** P-04 套餐登记 */
@Getter
@Setter
public class PeRegisterRequest {
    @NotNull(message = "患者不能为空")
    private Long patientId;
    @NotNull(message = "套餐不能为空")
    private Long packageId;
    @NotNull(message = "体检日期不能为空")
    private LocalDate examDate;
}
