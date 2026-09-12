package com.his.modules.billing.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SettlementQuery extends PageQuery {
    private LocalDate settleDate;
    private Long cashierId;
}
