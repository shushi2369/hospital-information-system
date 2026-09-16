package com.his.modules.medins.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettleQuery extends PageQuery {
    private String settleNo;
    private Integer status;
}
