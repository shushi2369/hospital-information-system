package com.his.modules.clinic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查/检验申请（applyType 由所选项目类别自动推导：3检查费→1检查，4检验费→2检验）。
 */
@Getter
@Setter
public class ExamApplicationCreateRequest {
    @NotNull(message = "收费项目不能为空")
    private Long chargeItemId;
    @Size(max = 256, message = "临床要求最长 256 字")
    private String requirement;
}
