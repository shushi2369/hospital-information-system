package com.his.modules.ris.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** R-03 检查预约 */
@Getter
@Setter
public class AppointRequest {
    @NotNull(message = "设备不能为空")
    private Long deviceId;
    @NotNull(message = "预约时间不能为空")
    private LocalDateTime apptTime;
}
