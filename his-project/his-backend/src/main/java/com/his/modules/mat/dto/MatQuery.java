package com.his.modules.mat.dto;

import com.his.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 物资/采购/领用通用分页查询 */
@Getter
@Setter
public class MatQuery extends PageQuery {
    private Integer category;
    private Integer status;
    private Long materialId;
    private Long deptId;
    /** 库存预警过滤：仅看低于安全库存 */
    private Boolean lowStock;
}
