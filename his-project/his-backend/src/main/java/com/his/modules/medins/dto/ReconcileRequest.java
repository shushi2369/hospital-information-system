package com.his.modules.medins.dto;

import lombok.Getter;
import lombok.Setter;

/** 对账请求（预留差异说明字段，Mock 对账自动判定）。 */
@Getter
@Setter
public class ReconcileRequest {
    private String remark;
}
