package com.his.modules.pharmacy.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovementQuery extends PageQuery {
    private Long drugId;
    private String refNo;
}
