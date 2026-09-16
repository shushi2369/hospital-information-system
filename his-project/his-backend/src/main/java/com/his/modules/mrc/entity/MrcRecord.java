package com.his.modules.mrc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 病案（出院未结即生成待归档；归档双前置：已结算+首页质控通过）。 */
@Getter
@Setter
@TableName("mrc_record")
public class MrcRecord extends BaseEntity {
    private String mrcNo;
    private Long admissionId;
    private Long patientId;
    private Integer archiveStatus;
    private Integer qcStatus;
    private Long qcBy;
    private LocalDateTime qcTime;
    private LocalDateTime archiveTime;
    private Integer status;

    @Version
    private Integer version;
}
