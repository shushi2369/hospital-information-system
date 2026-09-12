package com.his.modules.registration.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 跨模块输出的挂号单视图（医生工作站接诊、收费结算取数来源）。
 */
@Getter
@Setter
public class RegistrationDTO {
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
}
