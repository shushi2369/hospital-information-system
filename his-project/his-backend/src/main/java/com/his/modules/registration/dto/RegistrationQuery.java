package com.his.modules.registration.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RegistrationQuery extends PageQuery {
    private Long patientId;
    private Long doctorId;
    private Long deptId;
    private LocalDate regDate;
    private Integer status;
}
