package com.his.modules.whse.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseOrderQuery extends PageQuery {
    private Integer status;
    private Long supplierId;
}
