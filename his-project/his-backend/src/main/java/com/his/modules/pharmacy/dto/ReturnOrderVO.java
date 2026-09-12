package com.his.modules.pharmacy.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 退药单视图。 */
@Getter
@Setter
public class ReturnOrderVO {
    private Long id;
    private String returnNo;
    private String dispenseNo;
    private String rxNo;
    private String patientName;
    private String reason;
    private LocalDateTime returnTime;
    private String operatorName;
}
