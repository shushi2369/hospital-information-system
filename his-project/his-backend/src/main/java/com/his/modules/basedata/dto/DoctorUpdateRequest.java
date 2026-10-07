package com.his.modules.basedata.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * D-08 修改医生请求（工号 doctorCode、绑定账号 userId 均不可修改，故不含这两个字段）。
 * 未传的字段不修改（如仅停用只传 status=0）。
 */
@Data
public class DoctorUpdateRequest {

    /** 所属科室 bas_department.id（传入时必须是启用的临床科室） */
    @Min(value = 1, message = "所属科室不能为空或非法")
    private Long deptId;

    /** 医生姓名 */
    @NotBlank(message = "医生姓名不能为空")
    @Size(max = 64)
    private String doctorName;

    /** 职称：主任医师/副主任医师/主治医师/住院医师 */
    @NotBlank(message = "职称不能为空")
    @Size(max = 32)
    private String title;

    /** 是否专家：1 专家号 0 普通号（不传则不修改） */
    @Min(value = 0, message = "是否专家取值 1专家号 0普通号")
    @Max(value = 1, message = "是否专家取值 1专家号 0普通号")
    private Integer isExpert;

    /** 普通号挂号费（不传则不修改） */
    @DecimalMin(value = "0", message = "普通号挂号费不能为负数")
    private BigDecimal normalFee;

    /** 专家号挂号费（不传则不修改） */
    @DecimalMin(value = "0", message = "专家号挂号费不能为负数")
    private BigDecimal expertFee;

    /** 每日上午/下午各限挂数（不传则不修改） */
    @Min(value = 1, message = "每日限挂数至少为 1")
    private Integer dailyQuota;

    /** 状态：1 启用 0 停用（不传则不修改） */
    @Min(value = 0, message = "状态取值只能为 1（启用）或 0（停用）")
    @Max(value = 1, message = "状态取值只能为 1（启用）或 0（停用）")
    private Integer status;
}
