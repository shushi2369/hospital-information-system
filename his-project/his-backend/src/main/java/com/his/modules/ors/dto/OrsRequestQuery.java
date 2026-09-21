package com.his.modules.ors.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 手术申请分页查询 */
@Getter
@Setter
public class OrsRequestQuery extends PageQuery {
    private Long admissionId;
    private Long patientId;
    private Integer status;
    private LocalDate plannedDate;
}
