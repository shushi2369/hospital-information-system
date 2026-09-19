package com.his.modules.lis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code lis_critical_threshold} */
@Getter
@Setter
@TableName("lis_critical_threshold")
public class LisCriticalThreshold extends BaseEntity {
    private String itemName;
    private java.math.BigDecimal lowValue;
    private java.math.BigDecimal highValue;
    private Integer status;

    @Version
    private Integer version;
}
