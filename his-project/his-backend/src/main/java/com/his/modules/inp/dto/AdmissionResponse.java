package com.his.modules.inp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院响应（含患者/科室/病区/床位名称解析）。 */
@Getter
@Setter
public class AdmissionResponse {
    private Long id;
    private String admissionNo;
    private Long patientId;
    private String patientName;
    private String patientNo;
    private Long deptId;
    private String deptName;
    private Long wardId;
    private String wardName;
    private Long bedId;
    private String bedNo;
    private Long doctorId;
    private String doctorName;
    private Integer admissionType;
    private LocalDateTime admissionTime;
    private String plannedDiagnosis;
    private BigDecimal depositTotal;
    private Integer dischargeWay;
    private String dischargeDiagnosis;
    private LocalDateTime dischargeTime;
    private Integer status;
}
