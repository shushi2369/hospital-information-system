package com.his.modules.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * D-02 机构信息维护请求（机构编码 orgCode 不可修改，故不含该字段）。
 */
@Data
public class OrganizationUpdateRequest {

    /** 机构名称 */
    @NotBlank(message = "机构名称不能为空")
    private String orgName;

    /** 地址 */
    private String address;

    /** 联系电话 */
    private String phone;

    /** 状态：1 启用 0 停用（不传则不修改） */
    private Integer status;
}
