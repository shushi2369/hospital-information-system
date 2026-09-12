package com.his.modules.pharmacy.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReturnQuery extends PageQuery {
    private String returnNo;
}
