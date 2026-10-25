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
    /** 患者姓名模糊检索（一百四十六轮 F-L2：技师不知道就诊ID/患者ID，按姓名搜） */
    private String patientName;
}
