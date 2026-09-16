package com.his.modules.nur.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 体征（体温单数据源，录入范围校验）。 */
@Getter
@Setter
@TableName("nur_vital_sign")
public class NurVitalSign extends BaseEntity {
    private Long admissionId;
    private Long patientId;
    private java.time.LocalDateTime recordTime;
    private java.math.BigDecimal temperature;
    private Integer pulse;
    private Integer respiration;
    private Integer bpHigh;
    private Integer bpLow;
    private Integer spo2;
    private Integer painScore;
    private Long nurseId;
    private Integer status;
    @Version
    private Integer version;
}
