package com.his.modules.lis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code lis_request} */
@Getter
@Setter
@TableName("lis_request")
public class LisRequest extends BaseEntity {
    private String requestNo;
    private Long orderId;
    private Long admissionId;
    private Long patientId;
    private Long doctorId;
    private String specimenType;
    private Integer status;

    @Version
    private Integer version;
}
