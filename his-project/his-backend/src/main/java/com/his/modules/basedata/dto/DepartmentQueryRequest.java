package com.his.modules.basedata.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * D-03 科室列表查询条件（按类型/状态过滤，下拉与列表共用）。
 */
@Getter
@Setter
public class DepartmentQueryRequest extends PageQuery {

    /** 科室类型：1 临床科室 2 医技科室 3 药房 4 收费挂号 */
    private Integer deptType;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
