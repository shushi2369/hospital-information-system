package com.his.modules.ors.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code or_postop_record} */
@Getter
@Setter
@TableName("or_postop_record")
public class OrsPostopRecord extends BaseEntity {
    private Long requestId;
    private Integer recoveryScore;
    private Integer destination;
    private String followupNote;
    private LocalDateTime visitedAt;
    private Integer status;

    @Version
    private Integer version;
}
