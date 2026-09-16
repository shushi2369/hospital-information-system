package com.his.modules.inp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 转科记录（留痕）。 */
@Getter
@Setter
@TableName("inp_transfer")
public class InpTransfer extends BaseEntity {
    private Long admissionId;
    private Long fromDeptId;
    private Long toDeptId;
    private Long fromWardId;
    private Long toWardId;
    private java.time.LocalDateTime transferTime;
    private String reason;
    private Integer status;
    @Version
    private Integer version;
}
