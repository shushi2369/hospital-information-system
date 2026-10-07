package com.his.modules.cdss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code cdss_hit} */
@Getter
@Setter
@TableName("cdss_hit")
public class CdssHit extends BaseEntity {
    private Long orderId;
    private Long doctorId;
    /** 展示字段（不入库）：命中列表批量回填（一百零二轮裸 ID 清查 #4） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String doctorName;
    private Long ruleId;
    private String message;
    private Integer ignored;
    private java.time.LocalDateTime hitTime;

    @Version
    private Integer version;
}
