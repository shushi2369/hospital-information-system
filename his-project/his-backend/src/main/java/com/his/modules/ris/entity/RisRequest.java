package com.his.modules.ris.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code ris_request} */
@Getter
@Setter
@TableName("ris_request")
public class RisRequest extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private String requestNo;
    private Long orderId;
    private Long admissionId;
    /** 门诊就诊（门诊段） */
    private Long visitId;
    private Long patientId;
    /** 展示字段（不入库）：列表批量回填（一百零三轮患者裸列清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    private Long doctorId;
    private Integer modality;
    private String bodyPart;
    private String requirement;
    private Integer urgency;
    private Integer status;

    @Version
    private Integer version;
}
