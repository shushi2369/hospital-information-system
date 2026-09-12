package com.his.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志（append-only，无 status/version）。
 */
@Getter
@Setter
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String bizType;
    private String bizId;
    private String method;
    private String paramsJson;
    private String resultCode;
    private String ip;
    private String userAgent;
    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
