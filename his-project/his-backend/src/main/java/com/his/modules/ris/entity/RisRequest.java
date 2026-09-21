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
public class RisRequest extends BaseEntity {
    private String requestNo;
    private Long orderId;
    private Long admissionId;
    private Long patientId;
    private Long doctorId;
    private Integer modality;
    private String bodyPart;
    private String requirement;
    private Integer urgency;
    private Integer status;

    @Version
    private Integer version;
}
