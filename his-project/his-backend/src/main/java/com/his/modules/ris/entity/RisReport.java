package com.his.modules.ris.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code ris_report} 检查报告（书写 → 审核 → 发布，双签 reviewer≠reporter） */
@Getter
@Setter
@TableName("ris_report")
public class RisReport extends BaseEntity {
    private String reportNo;
    private Long requestId;
    private String finding;
    private String conclusion;
    private String criticalSign;
    private Integer criticalFlag;
    private Long reporterId;
    private LocalDateTime reportTime;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private Integer status;

    @Version
    private Integer version;
}
