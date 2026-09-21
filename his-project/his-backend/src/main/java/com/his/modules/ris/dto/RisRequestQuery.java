package com.his.modules.ris.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 检查申请/报告分页查询 */
@Getter
@Setter
public class RisRequestQuery extends PageQuery {
    private Long admissionId;
    private Long patientId;
    private Integer status;
    private Integer modality;
    private Integer urgency;
}
