package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_transfusion} */
@Getter
@Setter
@TableName("bb_transfusion")
public class BbTransfusion extends BaseEntity {
    private Long requestId;
    private Long bagId;
    private Long executorId;
    private Long checker1Id;
    private Long checker2Id;
    private String vitalBefore;
    private java.time.LocalDateTime startTime;
    private java.time.LocalDateTime endTime;
    private Integer outcome;
    private String note;

    @Version
    private Integer version;
}
