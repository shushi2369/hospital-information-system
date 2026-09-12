package com.his.modules.basedata.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * D-12 收费项目列表查询条件（按类别过滤，下拉与列表共用）。
 */
@Getter
@Setter
public class ChargeItemQueryRequest extends PageQuery {

    /** 类别：1 挂号费 2 诊查费 3 检查费 4 检验费 5 治疗费 6 材料费 7 药品费 */
    private Integer category;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
