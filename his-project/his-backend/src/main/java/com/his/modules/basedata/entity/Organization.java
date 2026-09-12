package com.his.modules.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织机构 bas_organization（《02 数据库设计》§4.1）。
 */
@Getter
@Setter
@TableName("bas_organization")
public class Organization extends BaseEntity {

    /** 机构编码（唯一，不可修改） */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 地址 */
    private String address;

    /** 联系电话 */
    private String phone;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
