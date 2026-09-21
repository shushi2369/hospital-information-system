package com.his.modules.emc.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 分诊/病例分页查询 */
@Getter
@Setter
public class EmcVisitQuery extends PageQuery {
    private Long patientId;
    private Integer centerType;
    private Integer status;
}
