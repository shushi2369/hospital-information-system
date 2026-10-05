package com.his.modules.doc.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 住院医嘱（状态机见《10》§2.3）。 */
@Getter
@Setter
@TableName("doc_order")
public class DocOrder extends BaseEntity {
    private String orderNo;
    /** 展示字段（不入库）：分页/审核队列批量回填（八十六轮契约审计） */
    @TableField(exist = false)
    private String patientName;
    @TableField(exist = false)
    private String doctorName;
    private Long admissionId;
    private Long patientId;
    private Long doctorId;
    private Integer orderClass;
    private Integer category;
    private String frequency;
    private java.time.LocalDateTime startTime;
    private java.time.LocalDateTime stopTime;
    private Integer skinTestFlag;
    private java.math.BigDecimal totalAmount;
    private Long reviewBy;
    private java.time.LocalDateTime reviewAt;
    private String reviewComment;
    private String stopReason;
    private String voidReason;
    private Integer status;
    @Version
    private Integer version;
}
