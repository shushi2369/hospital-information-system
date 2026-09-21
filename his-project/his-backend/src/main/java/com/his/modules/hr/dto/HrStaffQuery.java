package com.his.modules.hr.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 员工分页查询 */
@Getter
@Setter
public class HrStaffQuery extends PageQuery {
    private Long deptId;
    private Integer status;
    private String name;
}
