package com.his.modules.emc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code emc_visit} 五大中心登记（start_time 为时限基准） */
@Getter
@Setter
@TableName("emc_visit")
public class EmcVisit extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private String visitNo;
    private Long triageId;
    private Integer centerType;
    private Long doctorId;
    private Long patientId;
    /** 展示字段（不入库）：列表批量回填（一百零三轮患者裸列清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    private Long admissionId;
    private Long visitId;
    private LocalDateTime startTime;
    private Integer outcome;
    private LocalDateTime outcomeTime;
    private Integer status;

    @Version
    private Integer version;
}
