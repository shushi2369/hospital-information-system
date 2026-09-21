package com.his.modules.mat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code mat_requisition} */
@Getter
@Setter
@TableName("mat_requisition")
public class MatRequisition extends BaseEntity {
    private String reqNo;
    private Long materialId;
    private Long deptId;
    private Integer quantity;
    private Long applicantId;
    private String purpose;
    private Integer status;

    @Version
    private Integer version;
}
