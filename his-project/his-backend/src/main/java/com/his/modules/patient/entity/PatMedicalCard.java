package com.his.modules.patient.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊卡。
 */
@Getter
@Setter
@TableName("pat_medical_card")
public class PatMedicalCard extends BaseEntity {
    private Long patientId;
    private String cardNo;
    private Integer cardType;
    private Integer status;

    @Version
    private Integer version;
}
