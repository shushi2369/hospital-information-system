package com.his.modules.nur.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 护理排班。 */
@Getter
@Setter
@TableName("nur_schedule")
public class NurSchedule extends BaseEntity {
    private Long nurseId;
    private Long wardId;
    private java.time.LocalDate shiftDate;
    private Integer shiftType;
    private Integer status;
    @Version
    private Integer version;
}
