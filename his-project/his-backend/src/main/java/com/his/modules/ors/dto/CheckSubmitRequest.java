package com.his.modules.ors.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** OR-06 三方核查提交（双人签名） */
@Getter
@Setter
public class CheckSubmitRequest {
    @NotNull(message = "核查类型不能为空")
    @Min(1) @Max(3)
    private Integer checkType;
    @Valid
    @NotEmpty(message = "核查项不能为空")
    private List<CheckItem> items;
    @NotNull(message = "第二签名人不能为空")
    private Long checker2Id;

    @Getter
    @Setter
    public static class CheckItem {
        @NotNull(message = "核查项名称不能为空")
        @jakarta.validation.constraints.Size(max = 128, message = "核查项名称过长")
        private String item;
        @NotNull(message = "核查结果不能为空")
        private Boolean result;
    }
}
