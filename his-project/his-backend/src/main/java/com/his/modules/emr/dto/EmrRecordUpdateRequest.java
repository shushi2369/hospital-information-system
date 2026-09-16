package com.his.modules.emr.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EmrRecordUpdateRequest {
    @Size(max = 64, message = "标题最长 64 字")
    private String title;
    private Map<String, Object> content;
}
