package com.his.modules.pe.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pe_record} */
@Getter
@Setter
@TableName("pe_record")
public class PeRecord extends BaseEntity implements com.his.infrastructure.util.PatientNameBackfill.PatientIdCarrier {
    private String recordNo;
    private Long patientId;
    /** 展示字段（不入库）：列表批量回填（一百零三轮患者裸列清查） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String patientName;
    private Long packageId;
    private java.time.LocalDate examDate;
    private Integer status;

    @Version
    private Integer version;
}
