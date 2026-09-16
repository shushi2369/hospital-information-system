package com.his.modules.mrc.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 病案视图。 */
@Getter
@Setter
public class MrcRecordVO {
    private Long id;
    private String mrcNo;
    private Long admissionId;
    private Long patientId;
    private String patientName;
    private Integer archiveStatus;
    private Integer qcStatus;
    private LocalDateTime archiveTime;
}
