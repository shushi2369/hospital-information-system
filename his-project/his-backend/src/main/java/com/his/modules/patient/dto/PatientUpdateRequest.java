package com.his.modules.patient.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 档案修改：只允许改联系方式与病史（姓名/身份证/性别不可改，保证标识稳定）。
 */
@Getter
@Setter
public class PatientUpdateRequest {
    @Pattern(regexp = "\\d{11}", message = "手机号须为 11 位数字")
    private String phone;
    private String address;
    private String allergyHistory;
    private String pastHistory;
}
