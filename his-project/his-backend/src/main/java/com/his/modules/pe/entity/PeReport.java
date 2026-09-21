package com.his.modules.pe.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pe_report} */
@Getter
@Setter
@TableName("pe_report")
public class PeReport extends BaseEntity {
    private String reportNo;
    private Long recordId;
    private String summary;
    private Long doctorId;
    private java.time.LocalDateTime reportTime;
    private Integer status;

    @Version
    private Integer version;
}
