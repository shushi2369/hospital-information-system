package com.his.modules.basedata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医生 bas_doctor（《02 数据库设计》§4.3）。
 */
@Getter
@Setter
@TableName("bas_doctor")
public class Doctor extends BaseEntity {

    /** 关联登录账号 sys_user.id（唯一，绑定后不可修改） */
    private Long userId;

    /** 所属临床科室 bas_department.id */
    private Long deptId;

    /** 工号（唯一，不可修改） */
    private String doctorCode;

    /** 姓名（冗余自 sys_user.real_name） */
    private String doctorName;

    /** 职称：主任医师/副主任医师/主治医师/住院医师 */
    private String title;

    /** 是否专家：1 专家号 0 普通号 */
    private Integer isExpert;

    /** 普通号挂号费（快照进挂号单） */
    private BigDecimal normalFee;

    /** 专家号挂号费 */
    private BigDecimal expertFee;

    /** 每日上午/下午各限挂数（号源控制） */
    private Integer dailyQuota;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
