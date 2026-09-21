package com.his.modules.ors.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.his.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** {@code or_check_record} 手术安全核查单（麻醉前/切皮前/离室前，双人签名） */
@Getter
@Setter
@TableName("or_check_record")
public class OrsCheckRecord extends BaseEntity {
    private Long requestId;
    private Integer checkType;
    private String checkItems;
    private Long checker1Id;
    private Long checker2Id;
    private LocalDateTime checkedAt;

    @Version
    private Integer version;
}
