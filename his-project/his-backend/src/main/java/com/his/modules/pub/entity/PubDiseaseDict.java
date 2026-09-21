package com.his.modules.pub.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pub_disease_dict} */
@Getter
@Setter
@TableName("pub_disease_dict")
public class PubDiseaseDict extends BaseEntity {
    private String diseaseName;
    private String category;
    private Integer status;

    @Version
    private Integer version;
}
