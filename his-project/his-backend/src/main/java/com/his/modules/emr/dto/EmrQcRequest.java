package com.his.modules.emr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EmrQcRequest {
    @NotNull(message = "质控结论不能为空")
    private Boolean pass;
    private List<String> issues;
}
