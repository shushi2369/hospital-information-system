package com.his.modules.pe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** P-06 分项结果录入 */
@Getter
@Setter
public class PeResultRequest {
    @NotNull(message = "收费项目不能为空")
    private Long chargeItemId;
    @NotBlank(message = "项目名不能为空")
    private String itemName;
    @NotBlank(message = "结果值不能为空")
    @Size(max = 128, message = "结果值过长")
    private String resultValue;
    private Integer abnormalFlag;
    @Size(max = 256, message = "备注过长")
    private String note;
}
