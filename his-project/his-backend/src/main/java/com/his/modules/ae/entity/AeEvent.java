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
    /** 展示字段（不入库）：列表批量回填（一百零二轮裸 ID 清查 #5） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String deptName;
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
