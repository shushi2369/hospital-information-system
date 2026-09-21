package com.his.modules.pub.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pub_infectious_card} */
@Getter
@Setter
@TableName("pub_infectious_card")
public class PubInfectiousCard extends BaseEntity {
    private String cardNo;
    private Long visitId;
    private Long admissionId;
    private Long patientId;
    private Long doctorId;
    private String diseaseName;
    private String diseaseCategory;
    private java.time.LocalDate diagnoseDate;
    private Integer status;
    private Long publicDoctorId;
    private java.time.LocalDateTime reportTime;
    private String receiptNo;
    private java.time.LocalDateTime receiptTime;

    @Version
    private Integer version;
}
