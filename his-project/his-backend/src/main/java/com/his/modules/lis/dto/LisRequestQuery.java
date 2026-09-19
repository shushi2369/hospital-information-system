package com.his.modules.lis.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LisRequestQuery extends PageQuery {
    private Long admissionId;
    private Long patientId;
    private Integer status;
}
