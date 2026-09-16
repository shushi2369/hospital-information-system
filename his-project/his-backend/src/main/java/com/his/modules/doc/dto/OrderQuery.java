package com.his.modules.doc.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderQuery extends PageQuery {
    private Long admissionId;
    private Long patientId;
    private Integer category;
    private Integer status;
}
