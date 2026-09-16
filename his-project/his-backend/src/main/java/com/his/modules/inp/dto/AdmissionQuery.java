package com.his.modules.inp.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdmissionQuery extends PageQuery {
    private Long patientId;
    private Long deptId;
    private Long wardId;
    private Integer status;
    private String admissionNo;
}
