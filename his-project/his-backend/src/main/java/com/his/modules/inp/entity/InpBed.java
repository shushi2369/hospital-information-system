package com.his.modules.inp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 床位（分配走条件更新防一床两占）。 */
@Getter
@Setter
@TableName("inp_bed")
public class InpBed extends BaseEntity {
    private Long wardId;
    private String bedNo;
    private Integer bedStatus;
    private Long currentAdmissionId;
    private Long chargeItemId;
    private Integer status;
    @Version
    private Integer version;
}
