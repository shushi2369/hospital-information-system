package com.his.modules.mrc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 病案首页（ICD 编码 + 费用分类汇总）。 */
@Getter
@Setter
@TableName("mrc_homepage")
public class MrcHomepage extends BaseEntity {
    private Long admissionId;
    private String mainDiagnosisCode;
    private String mainDiagnosisName;
    private String otherDiagnoses;
    private String operationCode;
    private String chargeSummary;
    private Long coderId;
    private LocalDateTime codeTime;
    private Integer status;

    @Version
    private Integer version;
}
