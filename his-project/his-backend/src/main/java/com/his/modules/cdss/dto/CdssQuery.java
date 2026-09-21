package com.his.modules.cdss.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 规则/命中分页查询 */
@Getter
@Setter
public class CdssQuery extends PageQuery {
    private Integer ruleType;
    private Integer status;
    private Long orderId;
}
