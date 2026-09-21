package com.his.modules.cnt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code cnt_request} */
@Getter
@Setter
@TableName("cnt_request")
public class CntRequest extends BaseEntity {
    private String reqNo;
    private Long admissionId;
    private Long visitId;
    private Long patientId;
    private Long applicantId;
    private Long deptId;
    private Long consultDoctorId;
    private Integer urgent;
    private String reason;
    private Integer status;
    private java.time.LocalDateTime acceptTime;
    private String opinion;
    private java.time.LocalDateTime opinionTime;

    @Version
    private Integer version;
}
