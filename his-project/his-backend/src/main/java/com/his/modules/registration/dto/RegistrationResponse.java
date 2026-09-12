package com.his.modules.registration.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 挂号单响应（含患者/医生/科室名称解析）。
 */
@Getter
@Setter
public class RegistrationResponse {
    private Long id;
    private String regNo;
    private Long patientId;
    private String patientName;
    private String patientNo;
    private String cardNo;
    private Long deptId;
    private String deptName;
    private Long doctorId;
    private String doctorName;
    private LocalDate regDate;
    private Integer period;
    private Integer regType;
    private BigDecimal regFee;
    private BigDecimal consultationFee;
    private Integer queueNo;
    private Integer status;
    private Integer chargeStatus;
    private LocalDateTime createdAt;
}
