package com.his.modules.rpt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.his.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 区域平台上报表（五期-lite）。业务闭环触发入队，投递器推进状态。
 */
@Getter
@Setter
@TableName("rpt_upload")
public class RptUpload extends BaseEntity {
    public static final int TYPE_INFECTIOUS = 1;
    public static final int TYPE_ARCHIVE = 2;
    public static final int TYPE_INP_SETTLE = 3;

    public static final int STATUS_PENDING = 10;
    public static final int STATUS_UPLOADED = 20;
    public static final int STATUS_FAILED = 30;
    public static final int MAX_RETRY = 5;

    private String uploadNo;
    private Integer bizType;
    private Long bizId;
    private String bizNo;
    private String payload;
    private Integer status;
    private String receiptNo;
    private Integer retryCount;
    private String lastError;
    /** 失败退避：下次可重试时刻（NULL=立即可投）。只约束调度通道，手动投递不看（V50） */
    private LocalDateTime nextRetryAt;
    private LocalDateTime uploadedAt;

    @Version
    private Integer version;
}
