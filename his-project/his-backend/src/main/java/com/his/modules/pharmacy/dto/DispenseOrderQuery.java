package com.his.modules.pharmacy.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DispenseOrderQuery extends PageQuery {
    private String dispenseNo;
    private Long drugId;
    private LocalDate startDate;
    private LocalDate endDate;
}
