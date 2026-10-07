package com.his.modules.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code alert_critical} */
@Getter
@Setter
@TableName("alert_critical")
public class AlertCritical extends BaseEntity {
    private String alertNo;
    private Integer source;
    private Long requestId;
    private Long resultId;
    private Long patientId;
    private Long admissionId;
    private String itemName;
    private String criticalValue;
    private Long notifiedNurse;
    /** 展示字段（不入库）：列表批量回填（一百零二轮裸 ID 清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String notifiedNurseName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String confirmedDoctorName;
    private java.time.LocalDateTime notifiedAt;
    private Long confirmedDoctor;
    private java.time.LocalDateTime confirmedAt;
    private String handleNote;
    private Integer status;

    @Version
    private Integer version;
}
