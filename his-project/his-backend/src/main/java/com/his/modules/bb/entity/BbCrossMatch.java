package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_cross_match} */
@Getter
@Setter
@TableName("bb_cross_match")
public class BbCrossMatch extends BaseEntity {
    private Long requestId;
    private Long bagId;
    private String crossMethod;
    private Integer crossResult;
    private Long matcherId;
    private java.time.LocalDateTime matchTime;
    private String note;

    @Version
    private Integer version;
}
