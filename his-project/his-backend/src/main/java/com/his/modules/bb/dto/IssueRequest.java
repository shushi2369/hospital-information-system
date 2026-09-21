package com.his.modules.bb.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** B-09 发血（取血护士签收） */
@Getter
@Setter
public class IssueRequest {
    @NotNull(message = "血袋不能为空")
    private Long bagId;
    @NotNull(message = "取血护士不能为空")
    private Long receiverId;
}
