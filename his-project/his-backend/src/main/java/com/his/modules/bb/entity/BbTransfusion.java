package com.his.modules.bb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** {code bb_transfusion} */
@Getter
@Setter
@TableName("bb_transfusion")
public class BbTransfusion extends BaseEntity {
    private Long requestId;
    private Long bagId;
    private Long executorId;
    private Long checker1Id;
    /** 展示字段（不入库）：详情批量回填（一百零二轮裸 ID 清查 #6） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String checker1Name;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String checker2Name;
    private Long checker2Id;
    private String vitalBefore;
    private java.time.LocalDateTime startTime;
    private java.time.LocalDateTime endTime;
    private Integer outcome;
    private String note;

    @Version
    private Integer version;
}
