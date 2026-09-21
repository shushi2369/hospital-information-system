package com.his.modules.mat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code mat_material} */
@Getter
@Setter
@TableName("mat_material")
public class MatMaterial extends BaseEntity {
    private String materialCode;
    private String name;
    private Integer category;
    private String unit;
    private java.math.BigDecimal price;
    private Integer safeStock;
    private Integer status;

    @Version
    private Integer version;
}
