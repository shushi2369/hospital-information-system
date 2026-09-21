package com.his.modules.lis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code lis_report} */
@Getter
@Setter
@TableName("lis_report")
public class LisReport extends BaseEntity {
    private String reportNo;
    private Long requestId;
    private String resultSummary;
    private Long reporterId;
    private java.time.LocalDateTime reportTime;
    private Long auditedBy;
    private Integer status;
    /** 1 纳入互认(HR标识) */
    private Integer mutualFlag;
    /** 互认备注 */
    private String mutualNote;

    @Version
    private Integer version;
}
