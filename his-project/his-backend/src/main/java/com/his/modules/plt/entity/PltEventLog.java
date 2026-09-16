package com.his.modules.plt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 集成事件日志（outbox，append-only）。 */
@Getter
@Setter
@TableName("plt_event_log")
public class PltEventLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventNo;
    private String eventType;
    private String bizNo;
    private String payload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
