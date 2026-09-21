package com.his.modules.patient.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientQuery extends PageQuery {
    private String name;
    private String phone;
    private String patientNo;
    private String idCardNo;
    private String cardNo;
    /** 默认仅返回有效患者；管理场景显式传 true 查含停用（合并源患者等） */
    private Boolean includeDisabled;
}
