package com.his.modules.clinic.app;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收费/药房视角的就诊视图。
 */
@Getter
@Setter
public class BillingVisitDTO {
    private Long id;
    private String visitNo;
    private Long registrationId;
    private Long patientId;
    private String patientName;
    private LocalDate visitDate;
    private Integer visitStatus;
    private String doctorName;
}
