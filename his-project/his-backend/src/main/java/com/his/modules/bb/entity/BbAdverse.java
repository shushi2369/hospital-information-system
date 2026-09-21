package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_adverse} */
@Getter
@Setter
@TableName("bb_adverse")
public class BbAdverse extends BaseEntity {
    private Long requestId;
    private Integer type;
    private Integer severity;
    private String handleNote;
    private Long reporterId;
    private java.time.LocalDateTime reportTime;

    @Version
    private Integer version;
}
