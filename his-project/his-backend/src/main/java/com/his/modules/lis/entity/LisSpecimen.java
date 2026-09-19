package com.his.modules.lis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code lis_specimen} */
@Getter
@Setter
@TableName("lis_specimen")
public class LisSpecimen extends BaseEntity {
    private String specimenNo;
    private Long requestId;
    private java.time.LocalDateTime collectedAt;
    private Long collectorId;
    private Integer status;

    @Version
    private Integer version;
}
