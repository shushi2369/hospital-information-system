package com.his.modules.medins.dto;

import lombok.Getter;
import lombok.Setter;

/** 对账请求（预留差异说明字段，Mock 对账自动判定）。 */
@Getter
@Setter
public class ReconcileRequest {
    /** 一百一十七轮迁移专项【8】：200 上限——diff_reason 列宽 256，固定前缀 14 字 + 备注超长会 500 */
    @jakarta.validation.constraints.Size(max = 200, message = "对账备注最长 200 字")
    private String remark;
}
