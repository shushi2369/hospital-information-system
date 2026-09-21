package com.his.modules.emc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code emc_triage} 急诊分诊 */
@Getter
@Setter
@TableName("emc_triage")
public class EmcTriage extends BaseEntity {
    private String triageNo;
    private Long patientId;
    private Long visitId;
    private String chiefComplaint;
    private BigDecimal bodyTemp;
    private Integer pulse;
    private Integer respiration;
    private String bloodPressure;
    private Integer spo2;
    private Integer triageLevel;
    private Integer centerType;
    private Integer greenChannel;
    private Long triageNurseId;
    private LocalDateTime triageTime;
    private Integer status;

    @Version
    private Integer version;
}
