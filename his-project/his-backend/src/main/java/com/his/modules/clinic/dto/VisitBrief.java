package com.his.modules.clinic.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 就诊列表行（C-12 历史就诊）。
 */
@Getter
@Setter
public class VisitBrief {
    private Long id;
    private String visitNo;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private Long deptId;
    private String deptName;
    private LocalDate visitDate;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
