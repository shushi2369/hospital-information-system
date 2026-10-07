package com.his.modules.basedata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * D-05 修改/停用科室请求（科室编码 deptCode 不可修改，故不含该字段）。
 */
@Data
public class DepartmentUpdateRequest {

    /** 所属机构 bas_organization.id（不传则不修改） */
    @Min(value = 1, message = "所属机构不能为空或非法")
    private Long orgId;

    /** 科室名称 */
    @NotBlank(message = "科室名称不能为空")
    @Size(max = 64)
    private String deptName;

    /** 科室类型：1 临床科室 2 医技科室 3 药房 4 收费挂号（不传则不修改） */
    @Min(value = 1, message = "科室类型取值 1临床科室 2医技科室 3药房 4收费挂号")
    @Max(value = 4, message = "科室类型取值 1临床科室 2医技科室 3药房 4收费挂号")
    private Integer deptType;

    /** 位置（楼层/诊区，传 null 不修改） */
    @Size(max = 128)
    private String location;

    /** 状态：1 启用 0 停用（不传则不修改） */
    @Min(value = 0, message = "状态取值只能为 1（启用）或 0（停用）")
    @Max(value = 1, message = "状态取值只能为 1（启用）或 0（停用）")
    private Integer status;
}
