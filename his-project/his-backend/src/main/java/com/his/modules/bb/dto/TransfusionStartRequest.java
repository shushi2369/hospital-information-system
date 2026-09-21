package com.his.modules.bb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** B-10 输血开始（床边双人双签门禁） */
@Getter
@Setter
public class TransfusionStartRequest {
    @NotNull(message = "血袋不能为空")
    private Long bagId;
    @NotNull(message = "核对签 1 不能为空")
    private Long checker1Id;
    @NotNull(message = "核对签 2 不能为空")
    private Long checker2Id;
    private String vitalBefore;
}
