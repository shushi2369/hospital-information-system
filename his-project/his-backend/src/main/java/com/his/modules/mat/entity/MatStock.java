package com.his.modules.mat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code mat_stock} */
@Getter
@Setter
@TableName("mat_stock")
public class MatStock extends BaseEntity {
    private Long materialId;
    private Integer quantity;

    @Version
    private Integer version;
}
