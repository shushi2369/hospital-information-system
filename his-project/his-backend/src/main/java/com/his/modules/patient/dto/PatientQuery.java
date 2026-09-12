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
}
