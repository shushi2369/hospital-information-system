package com.his.modules.basedata.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * D-13 新增收费项目请求。
 */
@Data
public class ChargeItemCreateRequest {

    /** 项目编码（唯一） */
    @NotBlank(message = "项目编码不能为空")
    @Size(max = 32)
    private String itemCode;

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 64)
    private String itemName;

    /** 类别：1 挂号费 2 诊查费 3 检查费 4 检验费 5 治疗费 6 材料费 7 药品费 8 床位费 9 手术费 10 麻醉费 11 血费 */
    @NotNull(message = "费用类别不能为空")
    @Min(value = 1, message = "费用类别取值 1~11（挂号/诊查/检查/检验/治疗/材料/药品/床位/手术/麻醉/血费）")
    @Max(value = 11, message = "费用类别取值 1~11（挂号/诊查/检查/检验/治疗/材料/药品/床位/手术/麻醉/血费）")
    private Integer category;

    /** 单价 */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0", message = "单价不能为负数")
    private BigDecimal price;

    /** 计价单位（不传默认 次） */
    @Size(max = 16)
    private String unit;

    /** 状态：1 启用 0 停用（不传默认启用） */
    @Min(value = 0, message = "状态取值只能为 1（启用）或 0（停用）")
    @Max(value = 1, message = "状态取值只能为 1（启用）或 0（停用）")
    private Integer status;
}
