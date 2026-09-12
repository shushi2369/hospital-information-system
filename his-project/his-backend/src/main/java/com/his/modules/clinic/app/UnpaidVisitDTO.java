package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 收费窗口"未收费就诊列表"行。
 */
@Getter
@Setter
public class UnpaidVisitDTO {
    private Long visitId;
    private String visitNo;
    private String patientName;
    private LocalDate visitDate;
    private String doctorName;
}
