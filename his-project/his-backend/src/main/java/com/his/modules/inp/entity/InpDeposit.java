package com.his.modules.inp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 押金（只增不减）。 */
@Getter
@Setter
@TableName("inp_deposit")
public class InpDeposit extends BaseEntity {
    private Long admissionId;
    private java.math.BigDecimal amount;
    private Integer payMethod;
    private java.time.LocalDateTime payTime;
    private Long operatorId;
    private Integer status;
    @Version
    private Integer version;
}
