package com.his.modules.mrc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HomepageQcRequest {
    @NotNull(message = "质控结论不能为空")
    private Boolean pass;
}
