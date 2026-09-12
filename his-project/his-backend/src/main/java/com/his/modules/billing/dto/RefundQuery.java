package com.his.modules.billing.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RefundQuery extends PageQuery {
    private String refundNo;
    private Long billId;
    private Long operatorId;
    private LocalDate startDate;
    private LocalDate endDate;
}
