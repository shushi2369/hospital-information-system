package com.his.modules.patient.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者响应（idCardNo/phone 一律脱敏输出）。
 */
@Getter
@Setter
public class PatientResponse {
    private Long id;
    private String patientNo;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String idCardNo;
    private String address;
    private String allergyHistory;
    private String pastHistory;
    private LocalDateTime createdAt;
}
