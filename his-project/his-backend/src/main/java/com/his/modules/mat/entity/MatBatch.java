package com.his.modules.mat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code mat_batch} */
@Getter
@Setter
@TableName("mat_batch")
public class MatBatch extends BaseEntity {
    private Long materialId;
    private String batchNo;
    private java.time.LocalDate expireDate;
    private Integer quantity;
    private Integer status;

    @Version
    private Integer version;
}
