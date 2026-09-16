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
    private String category;

    @Version
    private Integer version;
}
