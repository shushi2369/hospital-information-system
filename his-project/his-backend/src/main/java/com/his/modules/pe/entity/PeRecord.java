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
public class PeRecord extends BaseEntity {
    private String recordNo;
    private Long patientId;
    private Long packageId;
    private java.time.LocalDate examDate;
    private Integer status;

    @Version
    private Integer version;
}
