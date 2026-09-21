package com.his.modules.ors.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** {@code or_surgery_request} */
@Getter
@Setter
@TableName("or_surgery_request")
public class OrsSurgeryRequest extends BaseEntity {
    private String requestNo;
    private Long admissionId;
    private Long patientId;
    private Long applicantDoctorId;
    private String surgeryName;
    private String surgeryCode;
    private String diagnosis;
    private LocalDate plannedDate;
    private Integer anesthesiaMethod;
    private Long surgeryItemId;
    private Long anesthesiaItemId;
    private BigDecimal surgeryPrice;
    private BigDecimal anesthesiaPrice;
    private LocalDateTime incisionTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer chargeStatus;

    @Version
    private Integer version;
}
