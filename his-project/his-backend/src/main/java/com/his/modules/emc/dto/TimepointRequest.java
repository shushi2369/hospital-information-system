package com.his.modules.emc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** E-06 时间节点录入（字典外节点拒绝） */
@Getter
@Setter
public class TimepointRequest {
    @NotBlank(message = "节点编码不能为空")
    private String nodeCode;
    @NotNull(message = "节点时间不能为空")
    private LocalDateTime nodeTime;
    private String note;
}
