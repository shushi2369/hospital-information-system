package com.his.modules.clinic.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class VisitPageQuery extends PageQuery {
    private Long patientId;
    private Long doctorId;
    private Long deptId;
    private LocalDate visitDate;
    private Integer status;
}
