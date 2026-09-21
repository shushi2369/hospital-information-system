package com.his.modules.pe.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 体检登记分页 */
@Getter
@Setter
public class PeRecordQuery extends PageQuery {
    private Long patientId;
    private Integer status;
}
