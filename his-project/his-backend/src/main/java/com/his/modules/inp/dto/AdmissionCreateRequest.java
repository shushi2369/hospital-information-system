package com.his.modules.inp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AdmissionCreateRequest {
    @NotNull(message = "患者不能为空")
    private Long patientId;
    @NotNull(message = "科室不能为空")
    private Long deptId;
    @NotNull(message = "病区不能为空")
    private Long wardId;
    @NotNull(message = "床位不能为空")
    private Long bedId;
    @NotNull(message = "主治医生不能为空")
    private Long doctorId;
    @NotNull(message = "入院类型不能为空")
    @Min(value = 1, message = "入院类型取值 1普通/2急诊/3转院入院")
    @Max(value = 3, message = "入院类型取值 1普通/2急诊/3转院入院")
    private Integer admissionType;
    @Size(max = 128, message = "入院诊断最长 128 字")
    private String plannedDiagnosis;
    @NotNull(message = "首笔押金不能为空")
    @DecimalMin(value = "0.01", message = "首笔押金必须大于 0")
    private BigDecimal depositAmount;
    @NotNull(message = "押金支付方式不能为空")
    @Min(value = 1, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    @Max(value = 4, message = "支付方式取值 1现金/2银行卡/3微信/4支付宝")
    private Integer payMethod;
}
