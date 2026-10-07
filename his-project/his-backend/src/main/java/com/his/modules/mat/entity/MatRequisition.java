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
    /** 展示字段（不入库）：列表批量回填（一百零二轮裸 ID 清查 #7） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String deptName;
    private Integer quantity;
    private Long applicantId;
    private String purpose;
    private Integer status;
    /** FEFO 拨发拆分[{batchNo,quantity}] */
    private String breakdown;

    @Version
    private Integer version;
}
