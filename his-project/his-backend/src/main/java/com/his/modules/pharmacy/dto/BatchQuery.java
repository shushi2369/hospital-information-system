package com.his.modules.pharmacy.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BatchQuery extends PageQuery {
    private Long drugId;
    private String batchNo;
    private Integer status;
}
