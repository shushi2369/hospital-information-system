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
 * D-10 新增药品请求。
 */
@Data
public class DrugCreateRequest {

    /** 药品编码（唯一） */
    @NotBlank(message = "药品编码不能为空")
    @Size(max = 32)
    private String drugCode;

    /** 药品名称（商品名/名称） */
    @NotBlank(message = "药品名称不能为空")
    @Size(max = 64)
    private String drugName;

    /** 通用名 */
    private String genericName;

    /** 规格，如 0.25g×24粒 */
    @NotBlank(message = "规格不能为空")
    @Size(max = 64)
    private String spec;

    /** 剂型（胶囊/片剂/颗粒/注射液） */
    private String dosageForm;

    /** 分类：1 西药 2 中成药 3 中药饮片 */
    @NotNull(message = "药品分类不能为空")
    @Min(value = 1, message = "药品分类取值 1西药 2中成药 3中药饮片")
    @Max(value = 3, message = "药品分类取值 1西药 2中成药 3中药饮片")
    private Integer category;

    /** 生产厂家 */
    @Size(max = 64)
    private String manufacturer;

    /** 最小发药单位（盒/瓶/支） */
    @NotBlank(message = "最小发药单位不能为空")
    @Size(max = 16)
    private String unit;

    /** 零售价 */
    @NotNull(message = "零售价不能为空")
    @DecimalMin(value = "0", message = "零售价不能为负数")
    private BigDecimal retailPrice;

    /** 库存预警下限（不传默认 0） */
    @DecimalMin(value = "0", message = "库存预警下限不能为负数")
    private BigDecimal stockWarningQty;

    /** 是否抗菌药物：1 是 0 否（不传默认否） */
    @Min(value = 0, message = "是否抗菌药物取值 1是 0否")
    @Max(value = 1, message = "是否抗菌药物取值 1是 0否")
    private Integer isAntibiotic;

    /** 状态：1 启用 0 停用（不传默认启用） */
    @Min(value = 0, message = "状态取值只能为 1（启用）或 0（停用）")
    @Max(value = 1, message = "状态取值只能为 1（启用）或 0（停用）")
    private Integer status;
}
