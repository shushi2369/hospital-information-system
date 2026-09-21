package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_issue} */
@Getter
@Setter
@TableName("bb_issue")
public class BbIssue extends BaseEntity {
    private Long requestId;
    private Long bagId;
    private Long issuerId;
    private Long receiverId;
    private java.time.LocalDateTime issueTime;

    @Version
    private Integer version;
}
