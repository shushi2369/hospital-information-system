package com.his.modules.bb.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 用血申请分页 */
@Getter
@Setter
public class BbRequestQuery extends PageQuery {
    private Long admissionId;
    private Long patientId;
    private Integer status;
}
