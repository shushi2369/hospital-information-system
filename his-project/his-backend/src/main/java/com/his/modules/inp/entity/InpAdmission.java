package com.his.modules.inp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院记录（核心聚合根，状态机见《10》§2.1）。 */
@Getter
@Setter
@TableName("inp_admission")
public class InpAdmission extends BaseEntity {
    private String admissionNo;
    private Long patientId;
    private Long deptId;
    private Long wardId;
    private Long bedId;
    private Long doctorId;
    private Integer admissionType;
    private LocalDateTime admissionTime;
    private String plannedDiagnosis;
    private BigDecimal depositTotal;
    private Integer dischargeWay;
    private String dischargeDiagnosis;
    private LocalDateTime dischargeTime;
    private Integer status;

    @Version
    private Integer version;
}
