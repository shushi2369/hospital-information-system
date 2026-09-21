package com.his.modules.ae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code ae_event} */
@Getter
@Setter
@TableName("ae_event")
public class AeEvent extends BaseEntity {
    private String eventNo;
    private Integer eventType;
    private Integer severity;
    private Long departmentId;
    private java.time.LocalDateTime eventTime;
    private String description;
    private Long reporterId;
    private Long qcId;
    private String handlerNote;
    private Long handlerId;
    private java.time.LocalDateTime closedTime;
    private Integer status;

    @Version
    private Integer version;
}
