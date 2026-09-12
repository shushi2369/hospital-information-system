package com.his.modules.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室 bas_department（《02 数据库设计》§4.2）。
 */
@Getter
@Setter
@TableName("bas_department")
public class Department extends BaseEntity {

    /** 所属机构 bas_organization.id */
    private Long orgId;

    /** 科室编码（唯一，不可修改） */
    private String deptCode;

    /** 科室名称 */
    private String deptName;

    /** 科室类型：1 临床科室 2 医技科室 3 药房 4 收费挂号 */
    private Integer deptType;

    /** 位置（楼层/诊区），挂号小票显示用 */
    private String location;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
