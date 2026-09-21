package com.his.modules.ors.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** {@code or_schedule} */
@Getter
@Setter
@TableName("or_schedule")
public class OrsSchedule extends BaseEntity {
    private String scheduleNo;
    private Long requestId;
    private Long roomId;
    private LocalDate surgeryDate;
    private Integer seqNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long surgeonId;
    private Long anesthetistId;
    private Long circulatingNurseId;
    private Long scrubNurseId;
    private Integer status;
    /** 槽位占用标记:1占用 NULL已释放（唯一索引 NULL 不生效→可重排） */
    private Integer slotActive;

    @Version
    private Integer version;
}
