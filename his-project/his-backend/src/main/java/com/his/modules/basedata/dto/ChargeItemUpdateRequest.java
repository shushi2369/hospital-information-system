package com.his.modules.basedata.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * D-14 修改/调价/停用收费项目请求（项目编码 itemCode 不可修改，故不含该字段）。
 */
@Data
public class ChargeItemUpdateRequest {

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 64)
    private String itemName;

    /** 类别：1 挂号费 2 诊查费 3 检查费 4 检验费 5 治疗费 6 材料费 7 药品费（不传则不修改） */
    @Min(value = 1, message = "费用类别取值 1挂号费 2诊查费 3检查费 4检验费 5治疗费 6材料费 7药品费")
    @Max(value = 7, message = "费用类别取值 1挂号费 2诊查费 3检查费 4检验费 5治疗费 6材料费 7药品费")
    private Integer category;

    /** 单价（不传则不修改；变更记操作审计） */
    @DecimalMin(value = "0", message = "单价不能为负数")
    private BigDecimal price;

    /** 计价单位（传 null 不修改） */
    @Size(max = 16)
    private String unit;

    /** 状态：1 启用 0 停用（不传则不修改） */
    @Min(value = 0, message = "状态取值只能为 1（启用）或 0（停用）")
    @Max(value = 1, message = "状态取值只能为 1（启用）或 0（停用）")
    private Integer status;
}
