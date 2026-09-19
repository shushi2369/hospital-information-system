package com.his.modules.whse.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {@code bas_supplier} */
@Getter
@Setter
@TableName("bas_supplier")
public class BasSupplier extends BaseEntity {
    private String supplierCode;
    private String supplierName;
    private String contact;
    private String phone;
    private Integer status;

    @Version
    private Integer version;
}
