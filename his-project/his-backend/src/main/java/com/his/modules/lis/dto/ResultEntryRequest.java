package com.his.modules.lis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 结果录入：fetch=true 走 Mock 仪器取数，否则按 rows 手工录入。 */
@Getter
@Setter
public class ResultEntryRequest {
    @NotNull(message = "申请单不能为空")
    private Long requestId;
    private Boolean fetch;
    private List<ResultRow> rows;

    @Getter
    @Setter
    public static class ResultRow {
        private String itemName;
        private String resultValue;
        private String unit;
        private String referenceRange;
        private Integer abnormalFlag;
    }
}
