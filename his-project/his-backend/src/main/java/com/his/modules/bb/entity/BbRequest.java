package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_request} */
@Getter
@Setter
@TableName("bb_request")
public class BbRequest extends BaseEntity {
    private String reqNo;
    private Long admissionId;
    private Long patientId;
    private Long doctorId;
    private Integer bloodType;
    private Integer rh;
    private Integer component;
    private Integer volumeMl;
    private String usePurpose;
    private Long reviewerId;
    private String reviewNote;
    private Integer status;

    @Version
    private Integer version;
}
