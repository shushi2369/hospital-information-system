package com.his.modules.emr.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmrRecordQuery extends PageQuery {
    private Long admissionId;
    private Long patientId;
    private Integer docType;
    private Integer status;
}
