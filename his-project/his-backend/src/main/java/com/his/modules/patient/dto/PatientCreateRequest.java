package com.his.modules.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PatientCreateRequest {
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotNull(message = "性别不能为空")
    private Integer gender;
    private LocalDate birthDate;
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "\\d{17}[0-9Xx]", message = "身份证号须为 18 位")
    private String idCardNo;
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "\\d{11}", message = "手机号须为 11 位数字")
    private String phone;
    private String address;
    private String allergyHistory;
    private String pastHistory;
}
