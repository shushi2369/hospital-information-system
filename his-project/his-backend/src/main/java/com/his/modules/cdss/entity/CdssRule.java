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
    /** type=4 过敏原关键字（患者过敏史包含即匹配） */
    private String allergyKeyword;
    /** type=3 生效年龄下限(岁) */
    private Integer ageMin;
    /** type=3 生效年龄上限(岁) */
    private Integer ageMax;
    private Integer level;
    private String message;
    private Integer status;

    @Version
    private Integer version;
}
