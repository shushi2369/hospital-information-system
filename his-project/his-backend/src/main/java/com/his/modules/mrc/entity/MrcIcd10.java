package com.his.modules.mrc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** ICD-10 字典。 */
@Getter
@Setter
@TableName("mrc_icd10")
public class MrcIcd10 extends BaseEntity {
    private String code;
    private String name;
    /** 1 启用 0 停用（一百零三轮漂移审计：字典停用后实体层无法过滤） */
    private Integer status;
    private String category;

    @Version
    private Integer version;
}
