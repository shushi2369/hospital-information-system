package com.his.modules.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * D-02 机构信息维护请求（机构编码 orgCode 不可修改，故不含该字段）。
 */
@Data
public class OrganizationUpdateRequest {

    /** 机构名称 */
    @jakarta.validation.constraints.Size(max = 64, message = "orgName最长 64 字")
    @NotBlank(message = "机构名称不能为空")
    private String orgName;

    /** 地址 */
    @jakarta.validation.constraints.Size(max = 128, message = "address最长 128 字")
    private String address;

    /** 联系电话 */
    @Size(max = 20)
    private String phone;

    /** 状态：1 启用 0 停用（不传则不修改） */
    private Integer status;
}
