package com.his.modules.ors.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code or_anesthesia_record} */
@Getter
@Setter
@TableName("or_anesthesia_record")
public class OrsAnesthesiaRecord extends BaseEntity {
    private String recordNo;
    private Long requestId;
    private Integer anesthesiaMethod;
    private Integer asaGrade;
    private Long anesthetistId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String drugNote;
    private String eventNote;
    private String vitalSample;
    private Integer status;

    @Version
    private Integer version;
}
