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
    private java.time.LocalDateTime notifiedAt;
    private Long confirmedDoctor;
    private java.time.LocalDateTime confirmedAt;
    private String handleNote;
    private Integer status;

    @Version
    private Integer version;
}
