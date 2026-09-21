package com.his.modules.ris.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** R-05 影像归档（fetch=true 走 Mock 通道，否则手工数量） */
@Getter
@Setter
public class ImageArchiveRequest {
    @NotNull(message = "取数方式不能为空")
    private Boolean fetch;
    @Min(0) @Max(9999)
    private Integer seriesCount;
    @Min(0) @Max(9999)
    private Integer imageCount;
    private String impressionText;
}
