package com.his.modules.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkinTestRequest {
    /** 阴性 / 阳性 */
    @NotBlank(message = "皮试结果不能为空")
    private String result;
    @Size(max = 128, message = "备注最长 128 字")
    private String note;
}
