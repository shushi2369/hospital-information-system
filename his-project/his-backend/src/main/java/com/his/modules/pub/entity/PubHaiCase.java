package com.his.modules.pub.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pub_hai_case} */
@Getter
@Setter
@TableName("pub_hai_case")
public class PubHaiCase extends BaseEntity {
    private String caseNo;
    private Long admissionId;
    private Long patientId;
    private Integer infectionType;
    private String infectionSite;
    private java.time.LocalDate diagnoseDate;
    private Long reporterId;
    private Integer status;
    private String confirmNote;
    private java.time.LocalDateTime confirmTime;

    @Version
    private Integer version;
}
