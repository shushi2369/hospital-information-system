package com.his.modules.emr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 病历文书（结构化节 JSON，状态机《10》§2.4）。 */
@Getter
@Setter
@TableName("emr_record")
public class EmrRecord extends BaseEntity {
    private String recordNo;
    private Long admissionId;
    private Integer docType;
    private String title;
    private String contentJson;
    private Long doctorId;
    private java.time.LocalDateTime recordTime;
    private Long qcBy;
    private java.time.LocalDateTime qcTime;
    private String qcIssues;
    private Integer status;
    @Version
    private Integer version;
}
