package com.his.modules.ris.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code ris_appointment} */
@Getter
@Setter
@TableName("ris_appointment")
public class RisAppointment extends BaseEntity {
    private Long requestId;
    private Long deviceId;
    private LocalDateTime apptTime;
    private Integer status;

    @Version
    private Integer version;
}
