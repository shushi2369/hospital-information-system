package com.his.modules.emc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code emc_timepoint} 五大中心时间节点 */
@Getter
@Setter
@TableName("emc_timepoint")
public class EmcTimepoint extends BaseEntity {
    private Long emcVisitId;
    private String nodeCode;
    private LocalDateTime nodeTime;
    private Long recorderId;
    private String note;

    @Version
    private Integer version;
}
