package com.his.modules.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code hr_staff} */
@Getter
@Setter
@TableName("hr_staff")
public class HrStaff extends BaseEntity {
    private String staffNo;
    private Long userId;
    private String name;
    private Long deptId;
    private String title;
    private String licenseNo;
    private String phone;
    private java.time.LocalDate entryDate;
    private java.time.LocalDate exitDate;
    private Integer status;

    @Version
    private Integer version;
}
