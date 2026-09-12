package com.his.modules.basedata.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * D-06 医生列表查询条件（按科室/是否专家过滤，下拉与列表共用）。
 */
@Getter
@Setter
public class DoctorQueryRequest extends PageQuery {

    /** 所属科室 bas_department.id */
    private Long deptId;

    /** 是否专家：1 专家号 0 普通号 */
    private Integer isExpert;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
