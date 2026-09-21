package com.his.modules.emc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code emc_node_dict} 五大中心节点字典（target_minutes 自登记时刻起算） */
@Getter
@Setter
@TableName("emc_node_dict")
public class EmcNodeDict extends BaseEntity {
    private String nodeCode;
    private String nodeName;
    private Integer centerType;
    private Integer seqNo;
    private Integer targetMinutes;
    private Integer status;

    @Version
    private Integer version;
}
