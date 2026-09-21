package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_blood_bag} */
@Getter
@Setter
@TableName("bb_blood_bag")
public class BbBloodBag extends BaseEntity {
    private String bagNo;
    private Integer bloodType;
    private Integer rh;
    private Integer component;
    private Integer volumeMl;
    private String bloodStation;
    private java.time.LocalDate collectDate;
    private java.time.LocalDate expireDate;
    private Integer status;

    @Version
    private Integer version;
}
