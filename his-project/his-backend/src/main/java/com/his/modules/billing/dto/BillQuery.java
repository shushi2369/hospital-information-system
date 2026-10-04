package com.his.modules.billing.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BillQuery extends PageQuery {
    private String billNo;
    private Long patientId;
    private Long admissionId;
    private Long cashierId;
    private LocalDate startDate;
    private LocalDate endDate;
}
