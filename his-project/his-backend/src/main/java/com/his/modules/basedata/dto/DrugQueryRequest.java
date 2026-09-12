package com.his.modules.basedata.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * D-09 药品分页查询条件（名称/编码模糊，分类/状态精确）。
 */
@Getter
@Setter
public class DrugQueryRequest extends PageQuery {

    /** 药品名称（模糊匹配） */
    private String drugName;

    /** 药品编码（模糊匹配） */
    private String drugCode;

    /** 分类：1 西药 2 中成药 3 中药饮片 */
    private Integer category;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
