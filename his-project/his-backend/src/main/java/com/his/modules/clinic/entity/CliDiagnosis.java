package com.his.modules.clinic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊断（ICD-10 编码一期可空）。
 */
@Getter
@Setter
@TableName("cli_diagnosis")
public class CliDiagnosis extends BaseEntity {
    private Long visitId;
    private String diagnosisCode;
    private String diagnosisName;
    private Integer diagnosisType;
    private Integer status;

    @Version
    private Integer version;
}
