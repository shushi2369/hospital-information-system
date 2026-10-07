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
public class PubHaiCase extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private String caseNo;
    private Long admissionId;
    private Long patientId;
    /** 展示字段（不入库）：列表批量回填（一百零三轮患者裸列清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
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
