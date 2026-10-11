package com.his.modules.doc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkinTestRequest {
    /** 阴性 / 阳性。一百一十二轮 B12：白名单——非枚举值会让皮试单进入
     *  "已登记但非阴性"态，dispense 永久拦截且登记不可重做（只能作废医嘱） */
    @NotBlank(message = "皮试结果不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "阴性|阳性", message = "皮试结果只能是 阴性 或 阳性")
    private String result;
    @Size(max = 128, message = "备注最长 128 字")
    private String note;
}
