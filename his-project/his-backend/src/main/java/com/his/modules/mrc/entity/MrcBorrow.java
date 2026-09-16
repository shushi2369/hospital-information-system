package com.his.modules.mrc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 病案借阅（同一病案同时仅一条借阅中）。 */
@Getter
@Setter
@TableName("mrc_borrow")
public class MrcBorrow extends BaseEntity {
    private Long mrcId;
    private Long borrowerId;
    private LocalDateTime borrowTime;
    private LocalDateTime expectReturnTime;
    private LocalDateTime returnTime;
    private Integer status;

    @Version
    private Integer version;
}
