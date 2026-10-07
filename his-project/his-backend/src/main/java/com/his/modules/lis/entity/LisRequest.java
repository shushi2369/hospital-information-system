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
public class LisRequest extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private String requestNo;
    private Long orderId;
    private Long admissionId;
    private Long patientId;
    /** 展示字段（不入库）：列表批量回填（一百零三轮患者裸列清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    private Long doctorId;
    /** 展示字段（不入库）：列表批量回填（一百轮浏览器走查——裸 ID 列） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String doctorName;
    private String specimenType;
    private Integer status;

    @Version
    private Integer version;
}
