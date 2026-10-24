package com.his.modules.cnt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 一百二十二轮【2】P3：完成会诊意见改 @RequestBody（原 @RequestParam 查询串传
 *  1024 字意见会超 Tomcat maxHttpHeaderSize 且落入 access log） */
@Getter
@Setter
public class CntCompleteRequest {
    @NotBlank(message = "会诊意见不能为空")
    @Size(max = 1024, message = "会诊意见最长 1024 字")
    private String opinion;
}
