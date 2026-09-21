package com.his.modules.cdss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code cdss_rule} */
@Getter
@Setter
@TableName("cdss_rule")
public class CdssRule extends BaseEntity {
    private String ruleCode;
    private Integer ruleType;
    private Long refAId;
    private Long refBId;
    private Integer level;
    private String message;
    private Integer status;

    @Version
    private Integer version;
}
