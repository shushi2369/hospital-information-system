package com.his.modules.clinic.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 医生候诊队列行（挂号信息 + 已存在的就诊 ID，前端可直接续接工作台，无需 sessionStorage 映射）。
 */
@Getter
@Setter
public class ClinicQueueItemDTO {
    private Long id;
    private String regNo;
    private Long patientId;
    private String patientName;
    private String patientNo;
    private Long deptId;
    private String deptName;
    private Long doctorId;
    private String doctorName;
    private LocalDate regDate;
    private Integer period;
    private Integer regType;
    private Integer queueNo;
    private Integer status;
    private Integer chargeStatus;
    private Long visitId;
}
