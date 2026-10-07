package com.his.modules.mrc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 病案借阅（同一病案同时仅一条借阅中）。 */
@Getter
@Setter
@TableName("mrc_borrow")
public class MrcBorrow extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private Long mrcId;
    private Long borrowerId;
    private LocalDateTime borrowTime;
    /** 列类型 DATE（V6），LocalDateTime 写入静默截断（一百零三轮漂移审计） */
    private java.time.LocalDate expectReturnTime;
    private LocalDateTime returnTime;
    private Integer status;

    /** 展示字段（不入库）：借阅台账列表批量回填（一百一十轮 M4） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String mrcNo;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long admissionId;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long patientId;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String borrowerName;

    @Version
    private Integer version;
}
