package com.his.modules.inp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 病区。 */
@Getter
@Setter
@TableName("inp_ward")
public class InpWard extends BaseEntity {
    private String wardCode;
    private String wardName;
    private Long deptId;
    private String location;
    private Integer status;
    @Version
    private Integer version;
}
