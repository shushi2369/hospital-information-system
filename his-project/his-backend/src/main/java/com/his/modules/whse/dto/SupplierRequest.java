package com.his.modules.whse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierRequest {
    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 32, message = "编码最长 32 位")
    private String supplierCode;
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 64, message = "名称最长 64 字")
    private String supplierName;
    private String contact;
    private String phone;
}
