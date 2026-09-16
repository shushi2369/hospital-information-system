package com.his.modules.plt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MergeRequest {
    @NotNull(message = "源主索引不能为空")
    private Long sourceMpiId;
    @NotNull(message = "目标主索引不能为空")
    private Long targetMpiId;
}
