package com.his.modules.pe.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code pe_package} */
@Getter
@Setter
@TableName("pe_package")
public class PePackage extends BaseEntity {
    private String packageNo;
    private String name;
    private java.math.BigDecimal price;
    private String items;
    private Integer status;

    @Version
    private Integer version;
}
