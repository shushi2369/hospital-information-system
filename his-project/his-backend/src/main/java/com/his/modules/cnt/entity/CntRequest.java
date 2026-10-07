package com.his.modules.cnt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code cnt_request} */
@Getter
@Setter
@TableName("cnt_request")
public class CntRequest extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private String reqNo;
    /** 展示字段（不入库）：列表批量回填（一百轮浏览器走查——裸 ID 列） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String deptName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String consultDoctorName;
    private Long admissionId;
    private Long visitId;
    private Long patientId;
    /** 展示字段（不入库）：列表批量回填（一百零三轮患者裸列清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    private Long applicantId;
    private Long deptId;
    private Long consultDoctorId;
    private Integer urgent;
    private String reason;
    private Integer status;
    private java.time.LocalDateTime acceptTime;
    private String opinion;
    private java.time.LocalDateTime opinionTime;

    @Version
    private Integer version;
}
